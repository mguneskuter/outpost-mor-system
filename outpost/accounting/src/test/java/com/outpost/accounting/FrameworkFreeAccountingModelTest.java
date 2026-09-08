package com.outpost.accounting;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrameworkFreeAccountingModelTest {
  private static final List<Class<?>> ACCOUNTING_TYPES =
      List.of(
          Register.class,
          Transaction.class,
          PaymentDetail.class,
          TransactionEvent.class,
          JournalEntry.class,
          JournalEntryLine.class,
          RegisterTypes.class,
          TransactionTypes.class,
          TransactionEventTypes.class,
          JournalEntryTypes.class);

  @Test
  void accountingModelDoesNotExposeFrameworkOrPersistenceTypes() {
    for (Class<?> accountingType : ACCOUNTING_TYPES) {
      assertPermitted(accountingType);
      for (Field field : accountingType.getDeclaredFields()) {
        assertPermitted(field.getType());
      }
      for (Method method : accountingType.getDeclaredMethods()) {
        assertPermitted(method.getReturnType());
        for (Class<?> parameterType : method.getParameterTypes()) {
          assertPermitted(parameterType);
        }
      }
      for (Constructor<?> constructor : accountingType.getDeclaredConstructors()) {
        for (Class<?> parameterType : constructor.getParameterTypes()) {
          assertPermitted(parameterType);
        }
      }
    }
  }

  private static void assertPermitted(Class<?> type) {
    String typeName = type.getName();
    assertFalse(typeName.startsWith("org.springframework."));
    assertFalse(typeName.startsWith("jakarta.persistence."));
    assertFalse(typeName.startsWith("org.apache.ibatis."));
    assertFalse(typeName.startsWith("org.flywaydb."));
    assertFalse(typeName.startsWith("java.sql."));
  }
}
