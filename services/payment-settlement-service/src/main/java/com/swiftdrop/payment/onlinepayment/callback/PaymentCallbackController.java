package com.swiftdrop.payment.onlinepayment.callback;

import com.swiftdrop.payment.onlinepayment.service.*;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentCallbackController {
    private final PaymentService service;
    public PaymentCallbackController(PaymentService service) { this.service=service; }
    @PostMapping(value="/api/v1/payments/callbacks/payhere",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void callback(@RequestParam MultiValueMap<String,String> fields) {
        if(fields.size()>30 || fields.values().stream().anyMatch(v->v.size()!=1 || v.getFirst().length()>1000))
            throw new PaymentProblem(400,"INVALID_CALLBACK","Invalid provider notification fields.");
        service.callback(fields.toSingleValueMap());
    }
}
