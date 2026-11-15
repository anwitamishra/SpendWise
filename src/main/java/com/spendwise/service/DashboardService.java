package com.spendwise.service;

import com.spendwise.model.DashboardSummary;
import com.spendwise.model.DashboardSummary.GroupSnippet;
import com.spendwise.model.ExpenseGroup;
import com.spendwise.model.GroupMember;
import com.spendwise.model.MemberBalance;
import com.spendwise.model.User;
import com.spendwise.repository.ExpenseGroupRepository;
import com.spendwise.repository.GroupMemberRepository;
import com.spendwise.repository.PersonalTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DashboardService {

    private final PersonalTransactionRepository transactionRepository;
    private final ExpenseGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final BalanceCalculationService balanceCalculationService;
    private final UserService userService;

    public DashboardService(PersonalTransactionRepository transactionRepository,
                            ExpenseGroupRepository groupRepository,
                            GroupMemberRepository memberRepository,
                            BalanceCalculationService balanceCalculationService,
                            UserService userService) {
        this.transactionRepository = transactionRepository;
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.balanceCalculationService = balanceCalculationService;
        this.userService = userService;
    }

    public DashboardSummary getDashboardSummary(String guestIdentifier, String period) {
        User user = userService.getUserByGuestIdentifier(guestIdentifier);

        LocalDate startDate = null;
        LocalDate endDate = LocalDate.now();

        if ("THIS_MONTH".equalsIgnoreCase(period)) {
            startDate = endDate.withDayOfMonth(1);
        } else if ("LAST_30_DAYS".equalsIgnoreCase(period)) {
            startDate = endDate.minusDays(30);
        } else if ("THIS_YEAR".equalsIgnoreCase(period)) {
            startDate = endDate.withDayOfYear(1);
        }
        // If "ALL" or null, startDate stays null

        // Personal totals
        Map<String, BigDecimal> totals = transactionRepository.getTotals(user.getId(), startDate, endDate);
        BigDecimal totalIncome = totals.getOrDefault("INCOME", BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal totalExpenses = totals.getOrDefault("EXPENSE", BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal netBalance = totalIncome.subtract(totalExpenses).max(BigDecimal.ZERO)
            .setScale(2, java.math.RoundingMode.HALF_UP);

        // Spending by category
        Map<String, BigDecimal> categoryExpenses = transactionRepository.getCategoryExpenses(user.getId(), startDate, endDate);

        // Recent personal transactions
        var recentTransactions = transactionRepository.getRecentTransactions(user.getId(), 5);

        // Monthly overview (last 6 months)
        var monthlyPoints = transactionRepository.getMonthlyPoints(user.getId(), 6);

        // Active groups for this user with their personal net balance
        List<ExpenseGroup> userGroups = groupRepository.findByUserId(user.getId());
        List<GroupSnippet> groupSnippets = new ArrayList<>();

        for (ExpenseGroup eg : userGroups) {
            Optional<GroupMember> gmOpt = memberRepository.findByGroupIdAndUserId(eg.getId(), user.getId());
            BigDecimal userBal = BigDecimal.ZERO;

            if (gmOpt.isPresent()) {
                Long memberId = gmOpt.get().getId();
                List<MemberBalance> balances = balanceCalculationService.calculateBalances(eg.getId());
                for (MemberBalance mb : balances) {
                    if (mb.getMemberId().equals(memberId)) {
                        userBal = mb.getNetBalance();
                        break;
                    }
                }
            }

            groupSnippets.add(new GroupSnippet(
                    eg.getId(),
                    eg.getGroupName(),
                    eg.getInviteCode(),
                    eg.getMemberCount() != null ? eg.getMemberCount() : 1,
                    userBal
            ));
        }

        DashboardSummary summary = new DashboardSummary();
        summary.setTotalIncome(totalIncome);
        summary.setTotalExpenses(totalExpenses);
        summary.setNetBalance(netBalance);
        summary.setRecentTransactions(recentTransactions);
        summary.setCategoryExpenses(categoryExpenses);
        summary.setMonthlyOverview(monthlyPoints);
        summary.setActiveGroups(groupSnippets);

        return summary;
    }
}
