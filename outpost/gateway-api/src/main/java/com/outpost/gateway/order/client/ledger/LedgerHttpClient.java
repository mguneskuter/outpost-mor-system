package com.outpost.gateway.order.client.ledger;

import com.outpost.accounting.api.CreatePaymentRequest;
import com.outpost.accounting.api.PaymentApi;
import com.outpost.gateway.order.client.LedgerClient;
import com.outpost.gateway.order.client.LedgerPayment;
import org.springframework.web.client.RestClientResponseException;

/** Creates payments through Ledger's HTTP contract. */
public final class LedgerHttpClient implements LedgerClient {
  private final PaymentApi paymentApi;

  /** Creates a Ledger client over the payment contract proxy. */
  public LedgerHttpClient(PaymentApi paymentApi) {
    this.paymentApi = paymentApi;
  }

  @Override
  public void createPayment(LedgerPayment payment) {
    CreatePaymentRequest request =
        new CreatePaymentRequest(
            payment.paymentReference(),
            payment.merchantCode(),
            payment.pspCode(),
            payment.shopperCountry().getIsoCode(),
            payment.shopperCountrySubdivision() == null
                ? null
                : payment.shopperCountrySubdivision().getCode(),
            payment.netAmount().quantity(),
            payment.taxAmount().quantity(),
            payment.grossAmount().quantity(),
            payment.grossAmount().currency().getCurrencyCode());
    try {
      paymentApi.create(request);
    } catch (RestClientResponseException exception) {
      boolean retryable = exception.getStatusCode().value() >= 500;
      throw new LedgerClientException("Ledger payment creation failed", retryable, exception);
    } catch (RuntimeException exception) {
      throw new LedgerClientException("Ledger payment creation failed", true, exception);
    }
  }
}
