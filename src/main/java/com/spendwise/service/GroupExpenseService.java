package com.spendwise.service;

import com.spendwise.dto.AddExpenseRequest;
import com.spendwise.dto.CustomSplitItem;
import com.spendwise.dto.ExpenseItemRequest;
import com.spendwise.dto.ExpenseContributionRequest;
import com.spendwise.exception.BadRequestException;
import com.spendwise.exception.ResourceNotFoundException;
import com.spendwise.model.ExpenseSplit;
import com.spendwise.model.GroupExpense;
import com.spendwise.model.GroupMember;
import com.spendwise.model.User;
import com.spendwise.repository.ExpenseSplitRepository;
import com.spendwise.repository.GroupExpenseRepository;
import com.spendwise.repository.GroupMemberRepository;
import com.spendwise.repository.ExpenseItemRepository;
import com.spendwise.repository.ExpenseContributionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class GroupExpenseService {

    private final GroupExpenseRepository expenseRepository;
    private final ExpenseSplitRepository splitRepository;
    private final GroupMemberRepository memberRepository;
    private final UserService userService;
    private final ExpenseItemRepository itemRepository;
    private final ExpenseContributionRepository contributionRepository;

    public GroupExpenseService(GroupExpenseRepository expenseRepository,
                               ExpenseSplitRepository splitRepository,
                               GroupMemberRepository memberRepository,
                               UserService userService,
                               ExpenseItemRepository itemRepository,
                               ExpenseContributionRepository contributionRepository) {
        this.expenseRepository = expenseRepository;
        this.splitRepository = splitRepository;
        this.memberRepository = memberRepository;
        this.userService = userService;
        this.itemRepository = itemRepository;
        this.contributionRepository = contributionRepository;
    }

    private void checkUserMembership(String guestIdentifier, Long groupId) {
        User user = userService.getUserByGuestIdentifier(guestIdentifier);
        if (!memberRepository.isMember(groupId, user.getId())) {
            throw new BadRequestException("You are not a member of this group");
        }
    }

    private List<ExpenseSplit> calculateSplits(BigDecimal totalAmount, String splitType,
                                              List<Long> participantIds, List<CustomSplitItem> customSplits,
                                              List<CustomSplitItem> percentages, List<ExpenseItemRequest> items,
                                              List<ExpenseContributionRequest> contributions,
                                              Long groupId) {
        List<GroupMember> groupMembers = memberRepository.findByGroupId(groupId);
        Set<Long> validMemberIds = new HashSet<>();
        for (GroupMember gm : groupMembers) {
            validMemberIds.add(gm.getId());
        }

        List<ExpenseSplit> splits = new ArrayList<>();

        if ("ITEMIZED".equalsIgnoreCase(splitType)) {
            if (items == null || items.isEmpty()) throw new BadRequestException("Add at least one bill item");
            Map<Long, BigDecimal> allocated = new LinkedHashMap<>();
            BigDecimal itemTotal = BigDecimal.ZERO;
            for (ExpenseItemRequest item : items) {
                if (item.getDescription() == null || item.getDescription().isBlank()) throw new BadRequestException("Each bill item needs a description");
                if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) throw new BadRequestException("Item amounts must be greater than zero");
                if (item.getMemberIds() == null || item.getMemberIds().isEmpty()) throw new BadRequestException("Assign each item to at least one member");
                Set<Long> uniqueMembers = new LinkedHashSet<>(item.getMemberIds());
                if (uniqueMembers.size() != item.getMemberIds().size()) throw new BadRequestException("An item cannot assign a member more than once");
                for (Long memberId : uniqueMembers) {
                    if (!validMemberIds.contains(memberId)) throw new BadRequestException("An item is assigned to a member outside this group");
                }
                BigDecimal itemAmount = item.getAmount().setScale(2, RoundingMode.HALF_UP);
                itemTotal = itemTotal.add(itemAmount);
                long itemCents = itemAmount.movePointRight(2).longValueExact();
                long base = itemCents / uniqueMembers.size();
                long remainder = itemCents % uniqueMembers.size();
                int index = 0;
                for (Long memberId : uniqueMembers) {
                    long cents = base + (index++ < remainder ? 1 : 0);
                    allocated.merge(memberId, BigDecimal.valueOf(cents, 2), (left, right) -> left.add(right));
                }
            }
            if (itemTotal.compareTo(totalAmount) != 0) throw new BadRequestException("Bill item amounts must add up to the expense total");
            allocated.forEach((memberId, share) -> {
                ExpenseSplit split = new ExpenseSplit();
                split.setMemberId(memberId);
                split.setShareAmount(share.setScale(2, RoundingMode.UNNECESSARY));
                splits.add(split);
            });
            return splits;
        }

        if ("EQUAL".equalsIgnoreCase(splitType) || "CONTRIBUTION_EQUAL".equalsIgnoreCase(splitType)) {
            if (participantIds == null || participantIds.isEmpty()) {
                throw new BadRequestException("At least one participant must be selected for equal split");
            }

            for (Long mId : participantIds) {
                if (!validMemberIds.contains(mId)) {
                    throw new BadRequestException("Participant id " + mId + " is not a valid member of this group");
                }
            }
            if (new HashSet<>(participantIds).size() != participantIds.size()) throw new BadRequestException("A participant cannot be selected more than once");

            List<Long> splitOrder = new ArrayList<>(participantIds);
            // Distribute indivisible remainder cents in participant order, independent of contribution size.
            long totalCents = totalAmount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValue();
            int n = splitOrder.size();
            long baseCents = totalCents / n;
            long remainderCents = totalCents % n;

            for (int i = 0; i < n; i++) {
                long shareCents = baseCents + (i < remainderCents ? 1 : 0);
                BigDecimal share = BigDecimal.valueOf(shareCents).divide(new BigDecimal("100"), 2, RoundingMode.UNNECESSARY);
                ExpenseSplit es = new ExpenseSplit();
                es.setMemberId(splitOrder.get(i));
                es.setShareAmount(share);
                splits.add(es);
            }

        } else if ("CUSTOM".equalsIgnoreCase(splitType) || "PERCENTAGE".equalsIgnoreCase(splitType)) {
            List<CustomSplitItem> requestedShares = "PERCENTAGE".equalsIgnoreCase(splitType) ? percentages : customSplits;
            if (requestedShares == null || requestedShares.isEmpty()) {
                throw new BadRequestException("Split details must be provided");
            }

            BigDecimal sum = BigDecimal.ZERO;
            BigDecimal percentageTotal = BigDecimal.ZERO;
            Set<Long> includedMemberIds = new HashSet<>();
            for (CustomSplitItem item : requestedShares) {
                if (item.getMemberId() == null) {
                    throw new BadRequestException("Member ID is required in custom split");
                }
                if (!validMemberIds.contains(item.getMemberId())) {
                    throw new BadRequestException("Member ID " + item.getMemberId() + " is not in this group");
                }
                if (!includedMemberIds.add(item.getMemberId())) throw new BadRequestException("A member can only have one split allocation");
                if (item.getShareAmount() == null || item.getShareAmount().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BadRequestException("Share amount cannot be negative or null");
                }

                BigDecimal share;
                if ("PERCENTAGE".equalsIgnoreCase(splitType)) {
                    if (item.getShareAmount().compareTo(new BigDecimal("100")) > 0) throw new BadRequestException("Each percentage must be at most 100");
                    percentageTotal = percentageTotal.add(item.getShareAmount());
                    share = totalAmount.multiply(item.getShareAmount()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                } else {
                    share = item.getShareAmount().setScale(2, RoundingMode.HALF_UP);
                }
                sum = sum.add(share);

                ExpenseSplit es = new ExpenseSplit();
                es.setMemberId(item.getMemberId());
                es.setShareAmount(share);
                splits.add(es);
            }

            if ("PERCENTAGE".equalsIgnoreCase(splitType) && percentageTotal.compareTo(new BigDecimal("100")) != 0) {
                throw new BadRequestException("Percentages must add up to exactly 100%");
            }
            if ("PERCENTAGE".equalsIgnoreCase(splitType) && sum.compareTo(totalAmount) != 0) {
                BigDecimal difference = totalAmount.subtract(sum).setScale(2, RoundingMode.HALF_UP);
                ExpenseSplit last = splits.get(splits.size() - 1);
                last.setShareAmount(last.getShareAmount().add(difference));
            } else if (sum.compareTo(totalAmount) != 0) {
                throw new BadRequestException("The sum of custom shares (" + sum + ") does not equal the total expense (" + totalAmount + ")");
            }

        } else {
            throw new BadRequestException("Invalid split type: " + splitType + ". Must be EQUAL, CONTRIBUTION_EQUAL, CUSTOM, PERCENTAGE, or ITEMIZED");
        }

        return splits;
    }

    @Transactional
    public GroupExpense addExpense(String guestIdentifier, Long groupId, AddExpenseRequest req) {
        checkUserMembership(guestIdentifier, groupId);
        String splitType = normalizeSplitType(req.getSplitType());
        List<ExpenseContributionRequest> contributions = null;
        BigDecimal requestedTotal = req.getTotalAmount();
        Long payerId = req.getPaidBy();
        if ("CONTRIBUTION_EQUAL".equals(splitType)) {
            ContributionSummary summary = normalizeContributions(req.getContributions(), groupId);
            contributions = summary.contributions();
            requestedTotal = summary.totalAmount();
            payerId = summary.topPayerId();
        }

        if (req.getDescription() == null || req.getDescription().trim().isEmpty()) {
            throw new BadRequestException("Expense description is required");
        }
        if (requestedTotal == null || requestedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Expense total amount must be greater than zero");
        }
        if (payerId == null) {
            throw new BadRequestException("Payer member is required");
        }
        if (req.getExpenseDate() == null) {
            throw new BadRequestException("Expense date is required");
        }

        GroupMember payer = memberRepository.findById(payerId)
                .orElseThrow(() -> new BadRequestException("Selected payer member does not exist"));
        if (!payer.getGroupId().equals(groupId)) {
            throw new BadRequestException("Selected payer does not belong to this group");
        }

        BigDecimal normalizedTotal = requestedTotal.setScale(2, RoundingMode.HALF_UP);

        List<Long> participantIds = "CONTRIBUTION_EQUAL".equals(splitType)
            ? getGroupMemberIds(groupId)
            : req.getParticipantIds();
        List<ExpenseSplit> splits = calculateSplits(normalizedTotal, splitType, participantIds, req.getCustomSplits(),
            req.getPercentages(), req.getItems(), contributions, groupId);

        GroupExpense expense = new GroupExpense();
        expense.setGroupId(groupId);
        expense.setDescription(req.getDescription().trim());
        expense.setTotalAmount(normalizedTotal);
        expense.setPaidBy(payer.getId());
        expense.setSplitType(splitType);
        expense.setExpenseDate(req.getExpenseDate());
        expense.setNotes(req.getNotes() != null ? req.getNotes().trim() : null);

        GroupExpense savedExpense = expenseRepository.save(expense);
        splitRepository.saveAll(savedExpense.getId(), splits);
        if ("ITEMIZED".equals(splitType)) itemRepository.replaceItems(savedExpense.getId(), req.getItems());
        if ("CONTRIBUTION_EQUAL".equals(splitType)) contributionRepository.replaceForExpense(savedExpense.getId(), contributions);

        savedExpense.setPaidByName(payer.getDisplayName());
        savedExpense.setSplits(splitRepository.findByExpenseId(savedExpense.getId()));
        savedExpense.setItems(itemRepository.findByExpenseId(savedExpense.getId()));
        savedExpense.setContributions(contributionRepository.findByExpenseId(savedExpense.getId()));

        return savedExpense;
    }

    public List<GroupExpense> getGroupExpenses(String guestIdentifier, Long groupId) {
        checkUserMembership(guestIdentifier, groupId);
        List<GroupExpense> expenses = expenseRepository.findByGroupId(groupId);
        for (GroupExpense exp : expenses) {
            exp.setSplits(splitRepository.findByExpenseId(exp.getId()));
            exp.setItems(itemRepository.findByExpenseId(exp.getId()));
            exp.setContributions(contributionRepository.findByExpenseId(exp.getId()));
        }
        return expenses;
    }

    public GroupExpense getExpenseById(String guestIdentifier, Long groupId, Long expenseId) {
        checkUserMembership(guestIdentifier, groupId);
        GroupExpense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + expenseId));

        if (!expense.getGroupId().equals(groupId)) {
            throw new ResourceNotFoundException("Expense does not belong to this group");
        }

        expense.setSplits(splitRepository.findByExpenseId(expenseId));
        expense.setItems(itemRepository.findByExpenseId(expenseId));
        expense.setContributions(contributionRepository.findByExpenseId(expenseId));
        return expense;
    }

    @Transactional
    public GroupExpense updateExpense(String guestIdentifier, Long groupId, Long expenseId, AddExpenseRequest req) {
        checkUserMembership(guestIdentifier, groupId);
        GroupExpense existing = getExpenseById(guestIdentifier, groupId, expenseId);
        String splitType = normalizeSplitType(req.getSplitType());
        List<ExpenseContributionRequest> contributions = null;
        BigDecimal requestedTotal = req.getTotalAmount();
        Long payerId = req.getPaidBy();
        if ("CONTRIBUTION_EQUAL".equals(splitType)) {
            ContributionSummary summary = normalizeContributions(req.getContributions(), groupId);
            contributions = summary.contributions();
            requestedTotal = summary.totalAmount();
            payerId = summary.topPayerId();
        }

        if (req.getDescription() == null || req.getDescription().trim().isEmpty()) {
            throw new BadRequestException("Expense description is required");
        }
        if (requestedTotal == null || requestedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Expense total amount must be greater than zero");
        }
        if (payerId == null) {
            throw new BadRequestException("Payer member is required");
        }
        if (req.getExpenseDate() == null) {
            throw new BadRequestException("Expense date is required");
        }

        GroupMember payer = memberRepository.findById(payerId)
                .orElseThrow(() -> new BadRequestException("Selected payer member does not exist"));
        if (!payer.getGroupId().equals(groupId)) {
            throw new BadRequestException("Selected payer does not belong to this group");
        }

        BigDecimal normalizedTotal = requestedTotal.setScale(2, RoundingMode.HALF_UP);

        List<Long> participantIds = "CONTRIBUTION_EQUAL".equals(splitType)
            ? getGroupMemberIds(groupId)
            : req.getParticipantIds();
        List<ExpenseSplit> newSplits = calculateSplits(normalizedTotal, splitType, participantIds, req.getCustomSplits(),
            req.getPercentages(), req.getItems(), contributions, groupId);

        existing.setDescription(req.getDescription().trim());
        existing.setTotalAmount(normalizedTotal);
        existing.setPaidBy(payer.getId());
        existing.setSplitType(splitType);
        existing.setExpenseDate(req.getExpenseDate());
        existing.setNotes(req.getNotes() != null ? req.getNotes().trim() : null);

        expenseRepository.update(existing);

        splitRepository.deleteByExpenseId(expenseId);
        splitRepository.saveAll(expenseId, newSplits);
        itemRepository.replaceItems(expenseId, "ITEMIZED".equals(splitType) ? req.getItems() : List.of());
        contributionRepository.replaceForExpense(expenseId, "CONTRIBUTION_EQUAL".equals(splitType) ? contributions : List.of());

        existing.setPaidByName(payer.getDisplayName());
        existing.setSplits(splitRepository.findByExpenseId(expenseId));
        existing.setItems(itemRepository.findByExpenseId(expenseId));
        existing.setContributions(contributionRepository.findByExpenseId(expenseId));

        return existing;
    }

    @Transactional
    public void deleteExpense(String guestIdentifier, Long groupId, Long expenseId) {
        checkUserMembership(guestIdentifier, groupId);
        GroupExpense existing = getExpenseById(guestIdentifier, groupId, expenseId);
        splitRepository.deleteByExpenseId(existing.getId());
        expenseRepository.delete(existing.getId());
    }

    private String normalizeSplitType(String requested) {
        if (requested == null || requested.isBlank() || "EQUAL".equalsIgnoreCase(requested)) return "EQUAL";
        if ("CUSTOM".equalsIgnoreCase(requested)) return "CUSTOM";
        if ("PERCENTAGE".equalsIgnoreCase(requested)) return "PERCENTAGE";
        if ("ITEMIZED".equalsIgnoreCase(requested)) return "ITEMIZED";
        if ("CONTRIBUTION_EQUAL".equalsIgnoreCase(requested)) return "CONTRIBUTION_EQUAL";
        throw new BadRequestException("Unsupported split type");
    }

    private ContributionSummary normalizeContributions(List<ExpenseContributionRequest> requested, Long groupId) {
        if (requested == null || requested.isEmpty()) throw new BadRequestException("Enter the amount each person paid");
        Set<Long> validIds = new HashSet<>();
        memberRepository.findByGroupId(groupId).forEach(member -> validIds.add(member.getId()));
        Set<Long> seenIds = new HashSet<>();
        List<ExpenseContributionRequest> normalized = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        Long topPayerId = null;
        BigDecimal highestPayment = BigDecimal.ZERO;
        for (ExpenseContributionRequest request : requested) {
            if (request == null || request.getMemberId() == null || !validIds.contains(request.getMemberId())) {
                throw new BadRequestException("Select valid group members for contributions");
            }
            if (!seenIds.add(request.getMemberId())) throw new BadRequestException("A member can only be listed once");
            if (request.getAmountPaid() == null || request.getAmountPaid().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Paid amounts must be zero or greater");
            }
            BigDecimal paid = request.getAmountPaid().setScale(2, RoundingMode.HALF_UP);
            ExpenseContributionRequest item = new ExpenseContributionRequest();
            item.setMemberId(request.getMemberId());
            item.setAmountPaid(paid);
            normalized.add(item);
            total = total.add(paid);
            if (topPayerId == null || paid.compareTo(highestPayment) > 0) {
                topPayerId = request.getMemberId();
                highestPayment = paid;
            }
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0) throw new BadRequestException("The total paid must be greater than zero");
        return new ContributionSummary(normalized, total, topPayerId);
    }

    private List<Long> getGroupMemberIds(Long groupId) {
        List<Long> memberIds = new ArrayList<>();
        for (GroupMember member : memberRepository.findByGroupId(groupId)) memberIds.add(member.getId());
        return memberIds;
    }

    private record ContributionSummary(List<ExpenseContributionRequest> contributions, BigDecimal totalAmount, Long topPayerId) {}
}
