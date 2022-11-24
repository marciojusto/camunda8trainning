package com.camunda.camunda8trainning.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CustomerCreditService {

    public Double getCustomerCredit(String customerId) {
        return Double.valueOf(customerId.substring(customerId.length()-2));
    }

    public Double deductCredit(String customerId, Double amount) {

        Double credit = getCustomerCredit(customerId);

        log.info("customer {} has credit of {}", customerId, credit);

        double openAmount;
        double deductedCredit;

        if (credit > amount) {
            deductedCredit = amount;
            openAmount = 0.0;
        } else {
            openAmount = amount - credit;
            deductedCredit = credit;
        }

        log.info("charged {} from the credit, open amount is {}", deductedCredit, openAmount);

        return amount;
    }
}
