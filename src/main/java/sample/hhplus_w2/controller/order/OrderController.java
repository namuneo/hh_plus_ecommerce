package sample.hhplus_w2.controller.order;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sample.hhplus_w2.domain.order.Order;
import sample.hhplus_w2.domain.order.OrderHistory;
import sample.hhplus_w2.domain.order.OrderItem;
import sample.hhplus_w2.service.order.OrderService;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(
            @RequestParam Long userId,
            @RequestParam Long cartId) {
        Order order = orderService.createOrder(userId, cartId);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{orderId}/pay")
    public ResponseEntity<Order> processPayment(@PathVariable Long orderId) {
        Order order = orderService.processPayment(orderId);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable Long orderId) {
        Order order = orderService.getOrder(orderId);
        return ResponseEntity.ok(order);
    }

    @GetMapping
    public ResponseEntity<List<Order>> getOrdersByUser(@RequestParam Long userId) {
        List<Order> orders = orderService.getOrdersByUser(userId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{orderId}/history")
    public ResponseEntity<List<OrderHistory>> getOrderHistory(@PathVariable Long orderId) {
        List<OrderHistory> history = orderService.getOrderHistory(orderId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{orderId}/items")
    public ResponseEntity<List<OrderItem>> getOrderItems(@PathVariable Long orderId) {
        List<OrderItem> items = orderService.getOrderItems(orderId);
        return ResponseEntity.ok(items);
    }
}
