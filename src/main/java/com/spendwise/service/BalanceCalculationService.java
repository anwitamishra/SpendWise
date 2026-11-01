package com.spendwise.service;

import com.spendwise.model.GroupMember;
import com.spendwise.model.MemberBalance;
import com.spendwise.model.SettlementSuggestion;
import com.spendwise.repository.ExpenseSplitRepository;
import com.spendwise.repository.GroupMemberRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class BalanceCalculationService {

    private final GroupMemberRepository memberRepository;
    private final ExpenseSplitRepository splitRepository;

    public BalanceCalculationService(GroupMemberRepository memberRepository,
                                     ExpenseSplitRepository splitRepository) {
        this.memberRepository = memberRepository;
        this.splitRepository = splitRepository;
    }

    public List<MemberBalance> calculateBalances(Long groupId) {
        List<GroupMember> members = memberRepository.findByGroupId(groupId);
        Map<Long, BigDecimal> paidMap = splitRepository.getTotalPaidByMember(groupId);
        Map<Long, BigDecimal> owedMap = splitRepository.getTotalOwedByMember(groupId);

        List<MemberBalance> balances = new ArrayList<>();

        for (GroupMember member : members) {
            BigDecimal paid = paidMap.getOrDefault(member.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal owed = owedMap.getOrDefault(member.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = paid.subtract(owed).setScale(2, RoundingMode.HALF_UP);

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

        // Greedy Min-Cash-Flow matching
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
}
