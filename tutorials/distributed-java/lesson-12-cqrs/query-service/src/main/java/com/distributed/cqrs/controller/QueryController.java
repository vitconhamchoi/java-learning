package com.distributed.cqrs.controller;

import com.distributed.cqrs.model.ProductView;
import com.distributed.cqrs.service.QueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/queries")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/products")
    public List<ProductView> getAllProducts() {
        return queryService.getAllProducts();
    }

    @GetMapping("/products/{id}")
    public ProductView getProduct(@PathVariable Long id) {
        return queryService.getProduct(id);
    }

    @GetMapping("/products/search")
    public List<ProductView> searchProducts(@RequestParam(required = false) String name,
                                             @RequestParam(required = false) Double minPrice,
                                             @RequestParam(required = false) Double maxPrice) {
        if (name != null) return queryService.searchByName(name);
        if (minPrice != null && maxPrice != null) return queryService.searchByPriceRange(minPrice, maxPrice);
        return queryService.getInStockProducts();
    }

    @PostMapping("/products/sync")
    public ProductView syncFromEvent(@RequestBody Map<String, Object> event) {
        return queryService.upsertFromEvent(event);
    }
}
