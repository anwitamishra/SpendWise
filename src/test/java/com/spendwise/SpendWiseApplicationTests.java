package com.spendwise;

import com.spendwise.dto.AddExpenseRequest;
import com.spendwise.dto.CreateGroupRequest;
import com.spendwise.dto.GuestUserRequest;
import com.spendwise.dto.PersonalTransactionRequest;
import com.spendwise.model.*;
import com.spendwise.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SpendWiseApplicationTests {

    @Autowired
    private UserService userService;

    @Autowired
    private PersonalTransactionService transactionService;

    @Autowired
    private ExpenseGroupService groupService;

    @Autowired
    private GroupExpenseService groupExpenseService;

    @Autowired
    private BalanceCalculationService balanceService;

    @Autowired
    private DashboardService dashboardService;

    private String guestId;

    @BeforeEach
    void setUp() {
        guestId = "test-guest-" + UUID.randomUUID();
        GuestUserRequest req = new GuestUserRequest();
        req.setGuestIdentifier(guestId);
        req.setDisplayName("Alice Tester");
        userService.getOrCreateGuestUser(req);
    }

    @Test
    void contextLoads() {
        assertNotNull(userService);
    }

    @Test
    void testPersonalTransactionCrudAndSummary() {
        // Create Income
        PersonalTransactionRequest inc = new PersonalTransactionRequest();
        inc.setTitle("Monthly Salary");
        inc.setAmount(new BigDecimal("50000.00"));
        inc.setTransactionType("INCOME");
        inc.setCategory("Salary");
        inc.setTransactionDate(LocalDate.now());
        PersonalTransaction savedInc = transactionService.createTransaction(guestId, inc);
        assertNotNull(savedInc.getId());

        // Create Expense
        PersonalTransactionRequest exp = new PersonalTransactionRequest();
        exp.setTitle("Grocery Run");
        exp.setAmount(new BigDecimal("2500.00"));
        exp.setTransactionType("EXPENSE");
        exp.setCategory("Food");
        exp.setTransactionDate(LocalDate.now());
        PersonalTransaction savedExp = transactionService.createTransaction(guestId, exp);
        assertNotNull(savedExp.getId());

        // Summary
        var summary = transactionService.getSummary(guestId, null, null);
        assertEquals(new BigDecimal("50000.00"), summary.get("totalIncome"));
        assertEquals(new BigDecimal("2500.00"), summary.get("totalExpenses"));
        assertEquals(new BigDecimal("47500.00"), summary.get("netBalance"));

        // Delete Expense
        transactionService.deleteTransaction(guestId, savedExp.getId());
        var summaryAfterDel = transactionService.getSummary(guestId, null, null);
        assertEquals(new BigDecimal("0.00"), summaryAfterDel.get("totalExpenses"));
    }

    @Test
    void testGroupCreationAndInviteCode() {
        CreateGroupRequest req = new CreateGroupRequest();
        req.setGroupName("Goa Trip 2026");
        req.setDescription("Weekend with college friends");

        ExpenseGroup group = groupService.createGroup(guestId, req);
        assertNotNull(group.getId());
        assertNotNull(group.getInviteCode());
        assertTrue(group.getInviteCode().startsWith("SW-"));

        // Preview group
        ExpenseGroup preview = groupService.previewGroupByInviteCode(group.getInviteCode());
        assertEquals("Goa Trip 2026", preview.getGroupName());

        // Second user joins
        String guest2 = "test-bob-" + UUID.randomUUID();
        GuestUserRequest bobReq = new GuestUserRequest();
        bobReq.setGuestIdentifier(guest2);
        bobReq.setDisplayName("Bob Tester");
        userService.getOrCreateGuestUser(bobReq);

        ExpenseGroup joined = groupService.joinGroup(guest2, group.getInviteCode());
        assertEquals(2, joined.getMemberCount());
    }

    @Test
    void testExpenseEqualSplittingWithExactCentRemainder() {
        // Setup group with 3 members
        CreateGroupRequest req = new CreateGroupRequest();
        req.setGroupName("Apartment 301");
        ExpenseGroup group = groupService.createGroup(guestId, req);

        String guest2 = "member-2-" + UUID.randomUUID();
        GuestUserRequest req2 = new GuestUserRequest();
        req2.setGuestIdentifier(guest2);
        req2.setDisplayName("Bob");
        userService.getOrCreateGuestUser(req2);
        groupService.joinGroup(guest2, group.getInviteCode());

        String guest3 = "member-3-" + UUID.randomUUID();
        GuestUserRequest req3 = new GuestUserRequest();
        req3.setGuestIdentifier(guest3);
        req3.setDisplayName("Charlie");
        userService.getOrCreateGuestUser(req3);
        groupService.joinGroup(guest3, group.getInviteCode());

        List<GroupMember> members = groupService.getGroupMembers(group.getId());
        assertEquals(3, members.size());

        // Split ₹100.00 among 3 people: exactly 33.34, 33.33, 33.33
        AddExpenseRequest expReq = new AddExpenseRequest();
        expReq.setDescription("Internet Bill");
        expReq.setTotalAmount(new BigDecimal("100.00"));
        expReq.setPaidBy(members.get(0).getId()); // Alice paid
        expReq.setSplitType("EQUAL");
        expReq.setExpenseDate(LocalDate.now());
        expReq.setParticipantIds(List.of(members.get(0).getId(), members.get(1).getId(), members.get(2).getId()));

        GroupExpense exp = groupExpenseService.addExpense(guestId, group.getId(), expReq);
        assertEquals(3, exp.getSplits().size());

        BigDecimal sumOfShares = exp.getSplits().stream()
                .map(ExpenseSplit::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("100.00"), sumOfShares, "Equal split must sum to exactly the total amount down to the cent");

        // Check Balances
        List<MemberBalance> balances = balanceService.calculateBalances(group.getId());
        MemberBalance aliceBal = balances.stream().filter(b -> b.getMemberId().equals(members.get(0).getId())).findFirst().orElseThrow();
        assertEquals("RECEIVES", aliceBal.getStatus());
        assertEquals(new BigDecimal("66.66"), aliceBal.getNetBalance()); // Paid 100, owes 33.34 -> net +66.66

        // Check Settlements
        List<SettlementSuggestion> settlements = balanceService.calculateSettlementSuggestions(group.getId());
        assertFalse(settlements.isEmpty());
        BigDecimal totalSettled = settlements.stream().map(SettlementSuggestion::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("66.66"), totalSettled);
    }
}
