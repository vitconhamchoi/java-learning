package com.distributed.cqrs.controller;

import com.distributed.cqrs.model.Product;
import com.distributed.cqrs.service.CommandService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/commands")
public class CommandController {

    private final CommandService commandService;

    public CommandController(CommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Product createProduct(@RequestBody Map<String, Object> request) {
        return commandService.createProduct(request);
    }

    @PatchMapping("/products/{id}/stock")
    public Product updateStock(@PathVariable Long id, @RequestParam int delta) {
        return commandService.updateStock(id, delta);
    }
}
