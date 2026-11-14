package com.spendwise;

import com.spendwise.dto.AddExpenseRequest;
import com.spendwise.dto.AuthResult;
import com.spendwise.dto.CreateGroupRequest;
import com.spendwise.dto.GuestUserRequest;
import com.spendwise.dto.PersonalTransactionRequest;
import com.spendwise.model.*;
import com.spendwise.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.mockito.ArgumentCaptor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
class SpendWiseApplicationTests {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

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
    void testAccountRegistrationLoginAndPasswordChange() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "tester_" + suffix;
        String email = suffix + "@example.com";
        AuthResult registered = authService.register(username, email, "initial-pass-123", "Alice Tester", guestId);

        assertEquals(guestId, registered.getUser().getGuestIdentifier());
        assertEquals(username, registered.getUser().getUsername());
        assertEquals(email, registered.getUser().getEmail());
        assertNotNull(authService.resolveSession(registered.getToken()));

        AuthResult signedIn = authService.login(username, "initial-pass-123");
        assertEquals(guestId, signedIn.getUser().getGuestIdentifier());
        assertNotNull(authService.resolveSession(signedIn.getToken()));
    }

        @Test
        void smartSplitSupportsPercentagesItemsAndSettlementTracking() throws Exception {
        CreateGroupRequest groupRequest = new CreateGroupRequest();
        groupRequest.setGroupName("Universal Split Test");
        ExpenseGroup group = groupService.createGroup(guestId, groupRequest);
        for (String name : List.of("Split Member Two", "Split Member Three")) {
            GuestUserRequest guest = new GuestUserRequest();
            guest.setGuestIdentifier("smart-split-" + UUID.randomUUID());
            guest.setDisplayName(name);
            userService.getOrCreateGuestUser(guest);
            groupService.joinGroup(guest.getGuestIdentifier(), group.getInviteCode());
        }
        List<GroupMember> members = groupService.getGroupMembers(group.getId());
        assertEquals(3, members.size());

        mockMvc.perform(post("/api/groups/" + group.getId() + "/expenses/smart-split")
                .header("X-Guest-Id", guestId).contentType("application/json")
                .content("{\"description\":\"Shared venue\",\"totalAmount\":100,\"paidBy\":" + members.get(0).getId() +
                    ",\"expenseDate\":\"" + LocalDate.now() + "\",\"splitType\":\"PERCENTAGE\",\"percentages\":[" +
                    "{\"memberId\":" + members.get(0).getId() + ",\"shareAmount\":50}," +
                    "{\"memberId\":" + members.get(1).getId() + ",\"shareAmount\":30}," +
                    "{\"memberId\":" + members.get(2).getId() + ",\"shareAmount\":20}]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.splitType").value("PERCENTAGE"))
            .andExpect(jsonPath("$.data.splits.length()").value(3));

        mockMvc.perform(post("/api/groups/" + group.getId() + "/expenses/smart-split")
                .header("X-Guest-Id", guestId).contentType("application/json")
                .content("{\"description\":\"Invalid allocation\",\"totalAmount\":100,\"paidBy\":" + members.get(0).getId() +
                    ",\"expenseDate\":\"" + LocalDate.now() + "\",\"splitType\":\"PERCENTAGE\",\"percentages\":[" +
                    "{\"memberId\":" + members.get(0).getId() + ",\"shareAmount\":40}," +
                    "{\"memberId\":" + members.get(1).getId() + ",\"shareAmount\":30}," +
                    "{\"memberId\":" + members.get(2).getId() + ",\"shareAmount\":20}]}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/groups/" + group.getId() + "/expenses/smart-split")
                .header("X-Guest-Id", guestId).contentType("application/json")
                .content("{\"description\":\"Itemized dinner\",\"totalAmount\":30,\"paidBy\":" + members.get(0).getId() +
                    ",\"expenseDate\":\"" + LocalDate.now() + "\",\"splitType\":\"ITEMIZED\",\"items\":[" +
                    "{\"description\":\"Shared starter\",\"amount\":10,\"memberIds\":[" + members.get(0).getId() + "," + members.get(1).getId() + "]}," +
                    "{\"description\":\"Main course\",\"amount\":20,\"memberIds\":[" + members.get(1).getId() + "," + members.get(2).getId() + "]}]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.splitType").value("ITEMIZED"))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.splits.length()").value(3));

        var planResponse = mockMvc.perform(post("/api/groups/" + group.getId() + "/settlements/suggestions")
                .header("X-Guest-Id", guestId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andReturn();
        var settlements = new com.fasterxml.jackson.databind.ObjectMapper().readTree(planResponse.getResponse().getContentAsString()).get("data");
        long pendingSettlementId = settlements.get(0).get("id").asLong();
        assertEquals("PENDING", settlements.get(0).get("status").asText());

        mockMvc.perform(post("/api/groups/" + group.getId() + "/settlements/" + pendingSettlementId + "/complete")
                .header("X-Guest-Id", guestId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/groups/" + group.getId() + "/balances")
                .header("X-Guest-Id", guestId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.settlements.length()").value(1));

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlements WHERE group_id = ? AND status = 'PENDING'", Integer.class, group.getId()));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlements WHERE group_id = ? AND status = 'COMPLETED'", Integer.class, group.getId()));

        MockMultipartFile invalidReceipt = new MockMultipartFile("file", "receipt.png", "image/png", "not an image".getBytes());
        mockMvc.perform(multipart("/api/groups/" + group.getId() + "/receipts").file(invalidReceipt).header("X-Guest-Id", guestId))
            .andExpect(status().isBadRequest());
        byte[] pngHeader = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        MockMultipartFile validReceipt = new MockMultipartFile("file", "receipt.png", "image/png", pngHeader);
        mockMvc.perform(multipart("/api/groups/" + group.getId() + "/receipts").file(validReceipt).header("X-Guest-Id", guestId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.originalFilename").value("receipt.png"));
        String storageKey = jdbcTemplate.queryForObject("SELECT storage_key FROM group_receipt_uploads WHERE group_id = ?", String.class, group.getId());
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("data/receipts", storageKey)));
        java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("data/receipts", storageKey));
        jdbcTemplate.update("DELETE FROM group_receipt_uploads WHERE group_id = ?", group.getId());
        }

        @Test
        void passwordChangeOtpSupportsInvalidExpiredResendAndSuccessfulChange() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "otp_" + suffix;
        String email = suffix + "@example.com";
        AuthResult registration = authService.register(username, email, "initial-pass-123", "OTP Tester", guestId);
        String authorization = "Bearer " + registration.getToken();
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doNothing().when(mailSender).send(any(MimeMessage.class));

        mockMvc.perform(post("/api/auth/password-change/request")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/password-change/complete").header("Authorization", authorization)
                .contentType("application/json").content("{\"newPassword\":\"replacement-pass-456\"}"))
            .andExpect(status().isUnauthorized());
        var firstRequest = mockMvc.perform(post("/api/auth/password-change/request").header("Authorization", authorization))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.maskedEmail").exists()).andReturn();
        assertFalse(firstRequest.getResponse().getContentAsString().contains("\"otp\""));
        String firstOtp = latestOtpSent();
        long userId = registration.getUser().getId();

        for (int attempt = 1; attempt <= 5; attempt++) {
            String wrongOtp = String.format("%06d", (Integer.parseInt(firstOtp) + attempt) % 1_000_000);
            mockMvc.perform(post("/api/auth/password-change/verify").header("Authorization", authorization)
                    .contentType("application/json").content("{\"otp\":\"" + wrongOtp + "\"}"))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/auth/password-change/verify").header("Authorization", authorization)
                .contentType("application/json").content("{\"otp\":\"" + firstOtp + "\"}"))
            .andExpect(status().isTooManyRequests());

        jdbcTemplate.update("UPDATE password_change_otps SET expires_at = ? WHERE user_id = ?",
            java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusSeconds(1)), userId);
        mockMvc.perform(post("/api/auth/password-change/verify").header("Authorization", authorization)
                .contentType("application/json").content("{\"otp\":\"" + firstOtp + "\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/password-change/request").header("Authorization", authorization))
            .andExpect(status().isTooManyRequests());
        jdbcTemplate.update("UPDATE password_change_otps SET created_at = ? WHERE user_id = ?",
            java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusSeconds(61)), userId);

        mockMvc.perform(post("/api/auth/password-change/request").header("Authorization", authorization))
            .andExpect(status().isOk());
        String secondOtp = latestOtpSent();
        assertNotEquals(firstOtp, secondOtp);

        mockMvc.perform(post("/api/auth/password-change/verify").header("Authorization", authorization)
                .contentType("application/json").content("{\"otp\":\"" + firstOtp + "\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/password-change/verify").header("Authorization", authorization)
                .contentType("application/json").content("{\"otp\":\"" + secondOtp + "\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/password-change/complete").header("Authorization", authorization)
                .contentType("application/json").content("{\"newPassword\":\"replacement-pass-456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Password updated successfully"));

        mockMvc.perform(post("/api/auth/password-change/complete").header("Authorization", authorization)
                .contentType("application/json").content("{\"newPassword\":\"third-password-456\"}"))
            .andExpect(status().isUnauthorized());

        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM password_change_otps WHERE user_id = ? AND consumed_at IS NULL", Integer.class, userId));
        assertEquals(username, authService.login(username, "replacement-pass-456").getUser().getUsername());
        }

        private String latestOtpSent() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        Object content = captor.getValue().getContent();
        Matcher matcher = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)").matcher(content.toString());
        assertTrue(matcher.find(), "Email should contain a six-digit OTP");
        return matcher.group(1);
        }

        @Test
        void registrationApiPersistsHashedCredentialsAndAllowsLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "api_" + suffix;
        String email = suffix + "@example.test";
        String password = "secure-pass-123";
        String registrationJson = "{\"username\":\"" + username + "\",\"email\":\"" + email +
            "\",\"password\":\"" + password + "\"}";

        var registrationResult = mockMvc.perform(post("/api/auth/register").contentType("application/json").content(registrationJson))
            .andReturn();
        String registrationResponse = registrationResult.getResponse().getContentAsString();
        assertEquals(201, registrationResult.getResponse().getStatus(), registrationResponse);
        assertTrue(registrationResponse.contains("\"success\":true"), registrationResponse);
        assertTrue(registrationResponse.contains("\"username\":\"" + username + "\""), registrationResponse);
        assertTrue(registrationResponse.contains("\"email\":\"" + email + "\""), registrationResponse);

        String passwordHash = jdbcTemplate.queryForObject(
            "SELECT password_hash FROM users WHERE username = ?", String.class, username);
        assertNotNull(passwordHash);
        assertNotEquals(password, passwordHash);
        assertTrue(passwordHash.startsWith("$2"), "Password must be stored as a BCrypt hash");

        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"identifier\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.user.username").value(username));

        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"identifier\":\"" + username + "\",\"password\":\"incorrect-password\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"email\":\"another-" + email +
                    "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"other_" + suffix + "\",\"email\":\"" + email +
                    "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"bad\",\"email\":\"not-an-email\",\"password\":\"tiny\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
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
        assertEquals(new BigDecimal("50000.00"), savedInc.getAmount());

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
        assertEquals(new BigDecimal("50000.00"), dashboardService.getDashboardSummary(guestId, "ALL").getTotalIncome());

        PersonalTransactionRequest overspend = new PersonalTransactionRequest();
        overspend.setTitle("Large purchase");
        overspend.setAmount(new BigDecimal("60000.00"));
        overspend.setTransactionType("EXPENSE");
        overspend.setCategory("Shopping");
        overspend.setTransactionDate(LocalDate.now());
        PersonalTransaction savedOverspend = transactionService.createTransaction(guestId, overspend);
        assertEquals(new BigDecimal("0.00"), transactionService.getSummary(guestId, null, null).get("netBalance"));
        assertEquals(new BigDecimal("0.00"), dashboardService.getDashboardSummary(guestId, "ALL").getNetBalance());
        transactionService.deleteTransaction(guestId, savedOverspend.getId());

        // Delete Expense
        transactionService.deleteTransaction(guestId, savedExp.getId());
        var summaryAfterDel = transactionService.getSummary(guestId, null, null);
        assertEquals(new BigDecimal("0.00"), summaryAfterDel.get("totalExpenses"));
    }

        @Test
        void contributionEqualSplitCalculatesExactWhoPaysWhomForExample() throws Exception {
        CreateGroupRequest groupRequest = new CreateGroupRequest();
        groupRequest.setGroupName("Contribution Split Example");
        ExpenseGroup group = groupService.createGroup(guestId, groupRequest);
        for (String name : List.of("Friend Two", "Friend Three")) {
            GuestUserRequest guest = new GuestUserRequest();
            guest.setGuestIdentifier("contribution-test-" + UUID.randomUUID());
            guest.setDisplayName(name);
            userService.getOrCreateGuestUser(guest);
            groupService.joinGroup(guest.getGuestIdentifier(), group.getInviteCode());
        }
        List<GroupMember> members = groupService.getGroupMembers(group.getId());

        var expenseResult = mockMvc.perform(post("/api/groups/" + group.getId() + "/expenses/smart-split")
                .header("X-Guest-Id", guestId).contentType("application/json")
                .content("{\"description\":\"Friends outing\",\"expenseDate\":\"" + LocalDate.now() +
                    "\",\"splitType\":\"CONTRIBUTION_EQUAL\",\"contributions\":[" +
                    "{\"memberId\":" + members.get(0).getId() + ",\"amountPaid\":100}," +
                    "{\"memberId\":" + members.get(1).getId() + ",\"amountPaid\":150}," +
                    "{\"memberId\":" + members.get(2).getId() + ",\"amountPaid\":300}]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.totalAmount").value(550.0))
            .andExpect(jsonPath("$.data.contributions.length()").value(3))
            .andReturn();
        var expense = new com.fasterxml.jackson.databind.ObjectMapper().readTree(expenseResult.getResponse().getContentAsString()).get("data");
        java.util.Map<Long, String> sharesByMember = new java.util.HashMap<>();
        expense.get("splits").forEach(split -> sharesByMember.put(split.get("memberId").asLong(), split.get("shareAmount").asText()));
        assertEquals("183.34", sharesByMember.get(members.get(0).getId()));
        assertEquals("183.33", sharesByMember.get(members.get(1).getId()));
        assertEquals("183.33", sharesByMember.get(members.get(2).getId()));

        var balanceResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/groups/" + group.getId() + "/balances").header("X-Guest-Id", guestId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.settlements.length()").value(2))
            .andReturn();
        var suggested = new com.fasterxml.jackson.databind.ObjectMapper().readTree(balanceResult.getResponse().getContentAsString())
            .get("data").get("settlements");
        assertEquals(members.get(0).getId().longValue(), suggested.get(0).get("fromMemberId").asLong());
        assertEquals(members.get(1).getId().longValue(), suggested.get(1).get("fromMemberId").asLong());
        assertEquals(members.get(2).getId().longValue(), suggested.get(0).get("toMemberId").asLong());
        assertEquals(members.get(2).getId().longValue(), suggested.get(1).get("toMemberId").asLong());
        assertEquals("83.34", suggested.get(0).get("amount").asText());
        assertEquals("33.33", suggested.get(1).get("amount").asText());
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
