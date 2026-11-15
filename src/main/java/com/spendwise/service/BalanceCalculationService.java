package com.spendwise.service;

import com.spendwise.model.GroupMember;
import com.spendwise.model.MemberBalance;
import com.spendwise.model.SettlementSuggestion;
import com.spendwise.repository.ExpenseSplitRepository;
import com.spendwise.repository.GroupMemberRepository;
import com.spendwise.repository.GroupSettlementRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class BalanceCalculationService {

    private final GroupMemberRepository memberRepository;
    private final ExpenseSplitRepository splitRepository;
    private final GroupSettlementRepository settlementRepository;

    public BalanceCalculationService(GroupMemberRepository memberRepository,
                                     ExpenseSplitRepository splitRepository,
                                     GroupSettlementRepository settlementRepository) {
        this.memberRepository = memberRepository;
        this.splitRepository = splitRepository;
        this.settlementRepository = settlementRepository;
    }

    public List<MemberBalance> calculateBalances(Long groupId) {
        List<GroupMember> members = memberRepository.findByGroupId(groupId);
        Map<Long, BigDecimal> paidMap = splitRepository.getTotalPaidByMember(groupId);
        Map<Long, BigDecimal> owedMap = splitRepository.getTotalOwedByMember(groupId);
        Map<Long, BigDecimal> settledNet = settlementRepository.getCompletedSettlementNet(groupId);

        List<MemberBalance> balances = new ArrayList<>();

        for (GroupMember member : members) {
            BigDecimal paid = paidMap.getOrDefault(member.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal owed = owedMap.getOrDefault(member.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = paid.subtract(owed).add(settledNet.getOrDefault(member.getId(), BigDecimal.ZERO)).setScale(2, RoundingMode.HALF_UP);

            String status = "SETTLED";
            if (net.compareTo(new BigDecimal("0.005")) > 0) {
                status = "RECEIVES";
            } else if (net.compareTo(new BigDecimal("-0.005")) < 0) {
                status = "OWES";
            } else {
                net = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }

            MemberBalance mb = new MemberBalance(
                    member.getId(),
                    member.getUserId(),
                    member.getDisplayName(),
                    paid,
                    owed,
                    net,
                    status
            );
            balances.add(mb);
        }

        return balances;
    }

    private static class FlowNode {
        Long memberId;
        String displayName;
        BigDecimal amount; // positive

        FlowNode(Long memberId, String displayName, BigDecimal amount) {
            this.memberId = memberId;
            this.displayName = displayName;
            this.amount = amount;
        }
    }

    public List<SettlementSuggestion> calculateSettlementSuggestions(Long groupId) {
        List<MemberBalance> balances = calculateBalances(groupId);

        List<FlowNode> debtors = new ArrayList<>();
        List<FlowNode> creditors = new ArrayList<>();

        BigDecimal threshold = new BigDecimal("0.01");

        for (MemberBalance mb : balances) {
            if (mb.getNetBalance().compareTo(threshold) >= 0) {
                creditors.add(new FlowNode(mb.getMemberId(), mb.getDisplayName(), mb.getNetBalance()));
            } else if (mb.getNetBalance().negate().compareTo(threshold) >= 0) {
                debtors.add(new FlowNode(mb.getMemberId(), mb.getDisplayName(), mb.getNetBalance().abs()));
            }
        }

        if (balances.size() <= 12) {
            List<FlowNode> nodes = new ArrayList<>();
            for (FlowNode debtor : debtors) nodes.add(new FlowNode(debtor.memberId, debtor.displayName, debtor.amount.negate()));
            for (FlowNode creditor : creditors) nodes.add(new FlowNode(creditor.memberId, creditor.displayName, creditor.amount));
            List<SettlementSuggestion> exact = minimumTransferPlan(nodes);
            if (exact != null) return exact;
        }

        // Greedy fallback keeps large groups efficient.
        List<SettlementSuggestion> suggestions = new ArrayList<>();

        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            // Sort to match largest debtor with largest creditor
            debtors.sort((a, b) -> b.amount.compareTo(a.amount));
            creditors.sort((a, b) -> b.amount.compareTo(a.amount));

            FlowNode debtor = debtors.get(0);
            FlowNode creditor = creditors.get(0);

            BigDecimal settleAmount = debtor.amount.min(creditor.amount).setScale(2, RoundingMode.HALF_UP);

            if (settleAmount.compareTo(BigDecimal.ZERO) > 0) {
                suggestions.add(new SettlementSuggestion(
                        debtor.memberId,
                        debtor.displayName,
                        creditor.memberId,
                        creditor.displayName,
                        settleAmount
                ));
            }

            debtor.amount = debtor.amount.subtract(settleAmount);
            creditor.amount = creditor.amount.subtract(settleAmount);

            if (debtor.amount.compareTo(threshold) < 0) {
                debtors.remove(0);
            }
            if (creditor.amount.compareTo(threshold) < 0) {
                creditors.remove(0);
            }
        }

        return suggestions;
    }

    private List<SettlementSuggestion> minimumTransferPlan(List<FlowNode> nodes) {
        List<FlowNode> active = nodes.stream().filter(node -> node.amount.compareTo(BigDecimal.ZERO) != 0)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<SettlementSuggestion> best = new ArrayList<>();
        minimumTransferSearch(active, 0, new ArrayList<>(), best);
        return best;
    }

    private void minimumTransferSearch(List<FlowNode> nodes, int start,
                                       List<SettlementSuggestion> current,
                                       List<SettlementSuggestion> best) {
        while (start < nodes.size() && nodes.get(start).amount.compareTo(BigDecimal.ZERO) == 0) start++;
        if (start == nodes.size()) {
            if (best.isEmpty() || current.size() < best.size()) {
                best.clear();
                best.addAll(current);
            }
            return;
        }
        if (!best.isEmpty() && current.size() >= best.size()) return;

        FlowNode first = nodes.get(start);
        Set<BigDecimal> attemptedAmounts = new HashSet<>();
        for (int i = start + 1; i < nodes.size(); i++) {
            FlowNode other = nodes.get(i);
            if (first.amount.signum() == other.amount.signum() || other.amount.compareTo(BigDecimal.ZERO) == 0 ||
                    !attemptedAmounts.add(other.amount)) continue;
            BigDecimal amount = first.amount.abs().min(other.amount.abs()).setScale(2, RoundingMode.HALF_UP);
            FlowNode debtor = first.amount.signum() < 0 ? first : other;
            FlowNode creditor = first.amount.signum() > 0 ? first : other;
            current.add(new SettlementSuggestion(debtor.memberId, debtor.displayName, creditor.memberId,
                    creditor.displayName, amount));
                BigDecimal originalFirst = first.amount;
                BigDecimal originalOther = other.amount;
            BigDecimal firstRemaining = first.amount.add(first.amount.signum() < 0 ? amount : amount.negate());
            BigDecimal otherRemaining = other.amount.add(other.amount.signum() < 0 ? amount : amount.negate());
            first.amount = firstRemaining;
            other.amount = otherRemaining;
            minimumTransferSearch(nodes, start + 1, current, best);
            first.amount = originalFirst;
            other.amount = originalOther;
            current.remove(current.size() - 1);
            if (firstRemaining.compareTo(BigDecimal.ZERO) == 0) break;
        }
    }
}
