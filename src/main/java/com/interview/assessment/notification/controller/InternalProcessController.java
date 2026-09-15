package com.interview.assessment.notification.controller;

import com.interview.assessment.notification.worker.DeliveryWorker;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal controller to trigger worker processing on-demand for local demos.
 * Only active under the `local-e2e` profile to avoid exposing in other environments.
 */
@RestController
@RequestMapping("/internal")
@Profile("local-e2e")
public class InternalProcessController {

    private final DeliveryWorker deliveryWorker;

    public InternalProcessController(DeliveryWorker deliveryWorker) {
        this.deliveryWorker = deliveryWorker;
    }

    @PostMapping("/process-now")
    public ResponseEntity<String> processNow() {
        deliveryWorker.tick();
        return ResponseEntity.accepted().body("triggered");
    }
}

