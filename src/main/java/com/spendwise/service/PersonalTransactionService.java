package com.spendwise.service;

import com.spendwise.dto.PersonalTransactionRequest;
import com.spendwise.exception.BadRequestException;
import com.spendwise.exception.ResourceNotFoundException;
import com.spendwise.model.PersonalTransaction;
import com.spendwise.model.User;
import com.spendwise.repository.PersonalTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PersonalTransactionService {

    private final PersonalTransactionRepository transactionRepository;
    private final UserService userService;

    public PersonalTransactionService(PersonalTransactionRepository transactionRepository, UserService userService) {
        this.transactionRepository = transactionRepository;
        this.userService = userService;
    }

    private void validateRequest(PersonalTransactionRequest req) {
        if (req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            throw new BadRequestException("Transaction title is required");
        }
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Transaction amount must be greater than zero");
        }
        if (req.getTransactionType() == null ||
                (!req.getTransactionType().equalsIgnoreCase("INCOME") && !req.getTransactionType().equalsIgnoreCase("EXPENSE"))) {
            throw new BadRequestException("Transaction type must be INCOME or EXPENSE");
        }
        if (req.getCategory() == null || req.getCategory().trim().isEmpty()) {
            throw new BadRequestException("Category is required");
        }
        if (req.getTransactionDate() == null) {
            throw new BadRequestException("Transaction date is required");
        }
    }

    @Transactional
    public PersonalTransaction createTransaction(String guestIdentifier, PersonalTransactionRequest req) {
        validateRequest(req);
        User user = userService.getUserByGuestIdentifier(guestIdentifier);

        PersonalTransaction pt = new PersonalTransaction();
        pt.setUserId(user.getId());
        pt.setTitle(req.getTitle().trim());
        pt.setAmount(req.getAmount().setScale(2, java.math.RoundingMode.HALF_UP));
        pt.setTransactionType(req.getTransactionType().toUpperCase().trim());
        pt.setCategory(req.getCategory().trim());
        pt.setDescription(req.getDescription() != null ? req.getDescription().trim() : null);
        pt.setTransactionDate(req.getTransactionDate());

        return transactionRepository.save(pt);
    }

    public List<PersonalTransaction> getTransactions(
            String guestIdentifier,
            String type,
            String category,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String sortBy,
            String sortDirection) {

        User user = userService.getUserByGuestIdentifier(guestIdentifier);
        return transactionRepository.findByUserIdWithFilters(
                user.getId(), type, category, startDate, endDate, search, sortBy, sortDirection);
    }

    public PersonalTransaction getTransactionById(String guestIdentifier, Long id) {
        User user = userService.getUserByGuestIdentifier(guestIdentifier);
        PersonalTransaction pt = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));

        if (!pt.getUserId().equals(user.getId())) {
            throw new ResourceNotFoundException("Transaction not found with id: " + id);
        }
        return pt;
    }

    @Transactional
    public PersonalTransaction updateTransaction(String guestIdentifier, Long id, PersonalTransactionRequest req) {
        validateRequest(req);
        PersonalTransaction existing = getTransactionById(guestIdentifier, id);

        existing.setTitle(req.getTitle().trim());
        existing.setAmount(req.getAmount().setScale(2, java.math.RoundingMode.HALF_UP));
        existing.setTransactionType(req.getTransactionType().toUpperCase().trim());
        existing.setCategory(req.getCategory().trim());
        existing.setDescription(req.getDescription() != null ? req.getDescription().trim() : null);
        existing.setTransactionDate(req.getTransactionDate());

        transactionRepository.update(existing);
        return existing;
    }

    @Transactional
    public void deleteTransaction(String guestIdentifier, Long id) {
        User user = userService.getUserByGuestIdentifier(guestIdentifier);
        int rows = transactionRepository.delete(id, user.getId());
        if (rows == 0) {
            throw new ResourceNotFoundException("Transaction not found with id: " + id);
        }
    }

    public Map<String, Object> getSummary(String guestIdentifier, LocalDate startDate, LocalDate endDate) {
        User user = userService.getUserByGuestIdentifier(guestIdentifier);
        Map<String, BigDecimal> totals = transactionRepository.getTotals(user.getId(), startDate, endDate);

        BigDecimal income = totals.getOrDefault("INCOME", BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal expense = totals.getOrDefault("EXPENSE", BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal balance = income.subtract(expense).setScale(2, java.math.RoundingMode.HALF_UP);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalIncome", income);
        summary.put("totalExpenses", expense);
        summary.put("netBalance", balance);
        return summary;
    }
}
