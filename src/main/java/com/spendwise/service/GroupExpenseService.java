package com.spendwise.service;

import com.spendwise.dto.AddExpenseRequest;
import com.spendwise.dto.CustomSplitItem;
import com.spendwise.exception.BadRequestException;
import com.spendwise.exception.ResourceNotFoundException;
import com.spendwise.model.ExpenseSplit;
import com.spendwise.model.GroupExpense;
import com.spendwise.model.GroupMember;
import com.spendwise.model.User;
import com.spendwise.repository.ExpenseSplitRepository;
import com.spendwise.repository.GroupExpenseRepository;
import com.spendwise.repository.GroupMemberRepository;
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

    public GroupExpenseService(GroupExpenseRepository expenseRepository,
                               ExpenseSplitRepository splitRepository,
                               GroupMemberRepository memberRepository,
                               UserService userService) {
        this.expenseRepository = expenseRepository;
        this.splitRepository = splitRepository;
        this.memberRepository = memberRepository;
        this.userService = userService;
    }

    private void checkUserMembership(String guestIdentifier, Long groupId) {
        User user = userService.getUserByGuestIdentifier(guestIdentifier);
        if (!memberRepository.isMember(groupId, user.getId())) {
            throw new BadRequestException("You are not a member of this group");
        }
    }

    private List<ExpenseSplit> calculateSplits(BigDecimal totalAmount, String splitType,
                                              List<Long> participantIds, List<CustomSplitItem> customSplits,
                                              Long groupId) {
        List<GroupMember> groupMembers = memberRepository.findByGroupId(groupId);
        Set<Long> validMemberIds = new HashSet<>();
        for (GroupMember gm : groupMembers) {
            validMemberIds.add(gm.getId());
        }

        List<ExpenseSplit> splits = new ArrayList<>();

        if ("EQUAL".equalsIgnoreCase(splitType)) {
            if (participantIds == null || participantIds.isEmpty()) {
                throw new BadRequestException("At least one participant must be selected for equal split");
            }

            for (Long mId : participantIds) {
                if (!validMemberIds.contains(mId)) {
                    throw new BadRequestException("Participant id " + mId + " is not a valid member of this group");
                }
            }

            // Remainder cent distribution
            long totalCents = totalAmount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValue();
            int n = participantIds.size();
            long baseCents = totalCents / n;
            long remainderCents = totalCents % n;

            for (int i = 0; i < n; i++) {
                long shareCents = baseCents + (i < remainderCents ? 1 : 0);
                BigDecimal share = BigDecimal.valueOf(shareCents).divide(new BigDecimal("100"), 2, RoundingMode.UNNECESSARY);
                ExpenseSplit es = new ExpenseSplit();
                es.setMemberId(participantIds.get(i));
                es.setShareAmount(share);
                splits.add(es);
            }

        } else if ("CUSTOM".equalsIgnoreCase(splitType)) {
            if (customSplits == null || customSplits.isEmpty()) {
                throw new BadRequestException("Custom split details must be provided");
            }

            BigDecimal sum = BigDecimal.ZERO;
            for (CustomSplitItem item : customSplits) {
                if (item.getMemberId() == null) {
                    throw new BadRequestException("Member ID is required in custom split");
                }
                if (!validMemberIds.contains(item.getMemberId())) {
                    throw new BadRequestException("Member ID " + item.getMemberId() + " is not in this group");
                }
                if (item.getShareAmount() == null || item.getShareAmount().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BadRequestException("Share amount cannot be negative or null");
                }

                BigDecimal share = item.getShareAmount().setScale(2, RoundingMode.HALF_UP);
                sum = sum.add(share);

                ExpenseSplit es = new ExpenseSplit();
                es.setMemberId(item.getMemberId());
                es.setShareAmount(share);
                splits.add(es);
            }

            if (sum.compareTo(totalAmount) != 0) {
                throw new BadRequestException("The sum of custom shares (" + sum + ") does not equal the total expense (" + totalAmount + ")");
            }

        } else {
            throw new BadRequestException("Invalid split type: " + splitType + ". Must be EQUAL or CUSTOM");
        }

        return splits;
    }

    @Transactional
    public GroupExpense addExpense(String guestIdentifier, Long groupId, AddExpenseRequest req) {
        checkUserMembership(guestIdentifier, groupId);

        if (req.getDescription() == null || req.getDescription().trim().isEmpty()) {
            throw new BadRequestException("Expense description is required");
        }
        if (req.getTotalAmount() == null || req.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Expense total amount must be greater than zero");
        }
        if (req.getPaidBy() == null) {
            throw new BadRequestException("Payer member is required");
        }
        if (req.getExpenseDate() == null) {
            throw new BadRequestException("Expense date is required");
        }

        GroupMember payer = memberRepository.findById(req.getPaidBy())
                .orElseThrow(() -> new BadRequestException("Selected payer member does not exist"));
        if (!payer.getGroupId().equals(groupId)) {
            throw new BadRequestException("Selected payer does not belong to this group");
        }

        BigDecimal normalizedTotal = req.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        String splitType = (req.getSplitType() != null && req.getSplitType().equalsIgnoreCase("CUSTOM")) ? "CUSTOM" : "EQUAL";

        List<ExpenseSplit> splits = calculateSplits(normalizedTotal, splitType, req.getParticipantIds(), req.getCustomSplits(), groupId);

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

        savedExpense.setPaidByName(payer.getDisplayName());
        savedExpense.setSplits(splitRepository.findByExpenseId(savedExpense.getId()));

        return savedExpense;
    }

    public List<GroupExpense> getGroupExpenses(String guestIdentifier, Long groupId) {
        checkUserMembership(guestIdentifier, groupId);
        List<GroupExpense> expenses = expenseRepository.findByGroupId(groupId);
        for (GroupExpense exp : expenses) {
            exp.setSplits(splitRepository.findByExpenseId(exp.getId()));
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
        return expense;
    }

    @Transactional
    public GroupExpense updateExpense(String guestIdentifier, Long groupId, Long expenseId, AddExpenseRequest req) {
        checkUserMembership(guestIdentifier, groupId);
        GroupExpense existing = getExpenseById(guestIdentifier, groupId, expenseId);

        if (req.getDescription() == null || req.getDescription().trim().isEmpty()) {
            throw new BadRequestException("Expense description is required");
        }
        if (req.getTotalAmount() == null || req.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Expense total amount must be greater than zero");
        }
        if (req.getPaidBy() == null) {
            throw new BadRequestException("Payer member is required");
        }
        if (req.getExpenseDate() == null) {
            throw new BadRequestException("Expense date is required");
        }

        GroupMember payer = memberRepository.findById(req.getPaidBy())
                .orElseThrow(() -> new BadRequestException("Selected payer member does not exist"));
        if (!payer.getGroupId().equals(groupId)) {
            throw new BadRequestException("Selected payer does not belong to this group");
        }

        BigDecimal normalizedTotal = req.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        String splitType = (req.getSplitType() != null && req.getSplitType().equalsIgnoreCase("CUSTOM")) ? "CUSTOM" : "EQUAL";

        List<ExpenseSplit> newSplits = calculateSplits(normalizedTotal, splitType, req.getParticipantIds(), req.getCustomSplits(), groupId);

        existing.setDescription(req.getDescription().trim());
        existing.setTotalAmount(normalizedTotal);
        existing.setPaidBy(payer.getId());
        existing.setSplitType(splitType);
        existing.setExpenseDate(req.getExpenseDate());
        existing.setNotes(req.getNotes() != null ? req.getNotes().trim() : null);

        expenseRepository.update(existing);

        splitRepository.deleteByExpenseId(expenseId);
        splitRepository.saveAll(expenseId, newSplits);

        existing.setPaidByName(payer.getDisplayName());
        existing.setSplits(splitRepository.findByExpenseId(expenseId));

        return existing;
    }

    @Transactional
    public void deleteExpense(String guestIdentifier, Long groupId, Long expenseId) {
        checkUserMembership(guestIdentifier, groupId);
        GroupExpense existing = getExpenseById(guestIdentifier, groupId, expenseId);
        splitRepository.deleteByExpenseId(existing.getId());
        expenseRepository.delete(existing.getId());
    }
}
