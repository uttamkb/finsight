package com.finsight.backend.service;

import com.finsight.backend.entity.Category;
import com.finsight.backend.entity.CategoryKeywordMapping;
import com.finsight.backend.repository.CategoryKeywordMappingRepository;
import com.finsight.backend.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankTransactionCategorizationServiceTest {

    @Mock
    private ClassificationService classificationService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryKeywordMappingRepository categoryKeywordMappingRepository;

    private BankTransactionCategorizationService categorizationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        categorizationService = new BankTransactionCategorizationService(classificationService, categoryRepository,
                categoryKeywordMappingRepository);
    }

    private CategoryKeywordMapping mockMapping(String keyword, String categoryName) {
        CategoryKeywordMapping mapping = new CategoryKeywordMapping();
        mapping.setKeyword(keyword);
        Category c = new Category();
        c.setName(categoryName);
        mapping.setCategory(c);
        return mapping;
    }

    @Test
    void testCategorize_TrustsAiCategory_WhenProvided() {
        String result = categorizationService.categorize("Vendor", "Desc", "Groceries", "DEBIT", "local_tenant");
        assertEquals("Groceries", result);
    }

    @Test
    void testCategorize_KeywordRuleMatch_Bescom() {
        when(categoryKeywordMappingRepository.findByTenantIdOrderByPriorityDesc(anyString()))
                .thenReturn(Arrays.asList(mockMapping("bescom", "Electricity")));

        String result = categorizationService.categorize(null, "BESCOM BLR 123", null, "DEBIT", "local_tenant");
        assertEquals("Electricity", result);
    }

    @Test
    void testCategorize_KeywordRuleMatch_Swiggy() {
        when(categoryKeywordMappingRepository.findByTenantIdOrderByPriorityDesc(anyString()))
                .thenReturn(Arrays.asList(mockMapping("swiggy", "Food & Dining")));

        String result = categorizationService.categorize("SWIGGY LIMITED", "Order 123", null, "DEBIT", "local_tenant");
        assertEquals("Food & Dining", result);
    }

    @Test
    void testCategorize_KeywordRuleMatch_Atm() {
        when(categoryKeywordMappingRepository.findByTenantIdOrderByPriorityDesc(anyString()))
                .thenReturn(Arrays.asList(mockMapping("atm ", "Cash Withdrawal")));

        String result = categorizationService.categorize(null, "ATM WDL 1000", null, "DEBIT", "local_tenant");
        assertEquals("Cash Withdrawal", result);
    }

    @Test
    void testCategorize_AiFallback_WhenNoRulesMatch() {
        when(categoryKeywordMappingRepository.findByTenantIdOrderByPriorityDesc(anyString()))
                .thenReturn(Arrays.asList());
        when(classificationService.classify(anyString(), anyString())).thenReturn("Personal");

        String result = categorizationService.categorize("Unknown", "Random Description", null, "DEBIT",
                "local_tenant");

        assertEquals("Personal", result);
        verify(classificationService, times(1)).classify(anyString(), anyString());
    }

    @Test
    void testCategorize_DefaultCredit_ReturnsIncomingTransfer() {
        when(categoryKeywordMappingRepository.findByTenantIdOrderByPriorityDesc(anyString()))
                .thenReturn(Arrays.asList());
        when(classificationService.classify(anyString(), anyString())).thenReturn(null);

        String result = categorizationService.categorize("Unknown", "Random", null, "CREDIT", "local_tenant");
        assertEquals("Incoming Transfer", result);
    }

    @Test
    void testGetOrCreateCategoryEntity_CreatesNewWithParent() {
        when(categoryRepository.findByNameAndTenantId(anyString(), anyString())).thenReturn(Optional.empty());

        Category savedCategory = new Category();
        savedCategory.setName("Electricity");
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArguments()[0]);

        Category result = categorizationService.getOrCreateCategoryEntity("Electricity", "DEBIT");

        assertNotNull(result);
        assertEquals("Electricity", result.getName());
        assertNotNull(result.getParentCategory());
        assertEquals("Utilities", result.getParentCategory().getName());

        verify(categoryRepository, atLeast(1)).save(any(Category.class));
    }
}
