package com.distributed.cb.config;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "inventory-service", url = "${inventory.service.url}")
public interface InventoryClient {

    @GetMapping("/api/inventory/check")
    InventoryResponse checkInventory(@RequestParam String productId, @RequestParam int quantity);

    record InventoryResponse(String productId, int available, boolean sufficient) {}
}
