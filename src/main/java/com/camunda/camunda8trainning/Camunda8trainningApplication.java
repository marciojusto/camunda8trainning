package com.camunda.camunda8trainning;

import com.camunda.camunda8trainning.service.CreditCardService;
import com.camunda.camunda8trainning.service.CustomerCreditService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.EnableZeebeClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

@SpringBootApplication
@EnableZeebeClient
@Slf4j
public class Camunda8trainningApplication {

    private final ZeebeClient zeebeClient;
    private final CustomerCreditService creditService;

    private final CreditCardService creditCardService;

    public Camunda8trainningApplication(ZeebeClient zeebeClient, CustomerCreditService creditService,
                                        CreditCardService creditCardService) {
        this.zeebeClient = zeebeClient;
        this.creditService = creditService;
        this.creditCardService = creditCardService;
    }

    public static void main(String[] args) {
        SpringApplication.run(Camunda8trainningApplication.class, args);
    }

    private static void logJob(final ActivatedJob job) {

        log.info("Job Received: [type: {}, process key: {}]\n[variables: {}]",
                 job.getType(),
                 job.getProcessInstanceKey(),
                 job.getVariables());
    }

    @JobWorker(type = "apply-discount")
    public void handleDiscountApplication(final ActivatedJob job) {

        logJob(job);

        Integer discount = (Integer) job.getVariablesAsMap().get("discount");

        Double orderTotal = (Double) job.getVariablesAsMap().get("orderTotal");

        Double discountedAmount = orderTotal - (orderTotal * discount / 100);

        zeebeClient.newCompleteCommand(job.getKey()).variables(Map.of("discountedAmount", discountedAmount)).send().join();
    }

    @JobWorker(type = "payment-invocation")
    public void handlePaymentInvocation(final ActivatedJob job) {

        logJob(job);

        zeebeClient.newPublishMessageCommand().messageName("paymentRequestMessage").correlationKey("").variables(job.getVariablesAsMap()).send().join();

        zeebeClient.newCompleteCommand(job.getKey()).send().join();
    }

    @JobWorker(type = "credit-deduction")
    public void handleCreditDeduction(final ActivatedJob job) {
        logJob(job);

        String customerId = (String) job.getVariablesAsMap()
                                        .get("customerId");
        Double orderTotal = (Double) job.getVariablesAsMap()
                                        .get("orderTotal");

        Double openAmount = creditService.deductCredit(customerId, orderTotal);
        Double customerCredit = creditService.getCustomerCredit(customerId);

        Map<String, Double> variables = Map.of("openAmount",
                                                 openAmount,
                                                 "customerCredit",
                                                 customerCredit);

        zeebeClient.newCompleteCommand(job.getKey()).variables(variables).send().join();
    }

    @JobWorker(type = "credit-card-charging")
    public void handleChargeCreditCard(final ActivatedJob job) {
        logJob(job);

        String cardNumber = (String) job.getVariablesAsMap().get("cardNumber");
        String cvc = (String) job.getVariablesAsMap().get("CVC");
        String expiryDate = (String) job.getVariablesAsMap().get("expiryDate");

        Double openAmount = (Double) job.getVariablesAsMap().get("openAmount");

        try {
            creditCardService.chargeAmount(cardNumber, cvc, expiryDate, openAmount);

            zeebeClient.newCompleteCommand(job.getKey()).send().join();

        }
        catch (IllegalArgumentException e) {
            zeebeClient.newThrowErrorCommand(job).errorCode("creditCardChargeError").send();
        }
        catch (Exception e) {
            StringWriter sw = new StringWriter();

            e.printStackTrace(new PrintWriter(sw));

            zeebeClient.newFailCommand(job).retries(0).errorMessage("Credit card expired").send();
        }
    }

    @JobWorker(type = "payment-completion")
    public void handlePaymentCompletion(final ActivatedJob job) {

        logJob(job);

        String orderId = (String) job.getVariablesAsMap().get("orderId");

        zeebeClient.newPublishMessageCommand().messageName("paymentCompletedMessage").correlationKey(orderId).variables(job.getVariablesAsMap()).send().join();

        zeebeClient.newCompleteCommand(job.getKey()).send().join();
    }
}
