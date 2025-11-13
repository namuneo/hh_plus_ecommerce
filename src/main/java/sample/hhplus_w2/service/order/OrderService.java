package sample.hhplus_w2.service.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sample.hhplus_w2.domain.cart.CartItem;
import sample.hhplus_w2.domain.order.*;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.repository.cart.CartItemRepository;
import sample.hhplus_w2.repository.order.OrderHistoryRepository;
import sample.hhplus_w2.repository.order.OrderItemRepository;
import sample.hhplus_w2.repository.order.OrderRepository;
import sample.hhplus_w2.repository.product.ProductRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                           OrderHistoryRepository orderHistoryRepository, CartItemRepository cartItemRepository,
                           ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderHistoryRepository = orderHistoryRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public Order createOrder(Long userId, Long cartId) {
        List<CartItem> cartItems = cartItemRepository.findByCartId(cartId);
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("장바구니가 비어있습니다.");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CartItem item : cartItems) {
            totalAmount = totalAmount.add(item.getTotalPrice());
        }

        Order order = Order.create(userId, totalAmount, BigDecimal.ZERO, 30);
        order = orderRepository.save(order);

        for (CartItem cartItem : cartItems) {
            OrderItem orderItem = OrderItem.create(
                    order.getId(),
                    cartItem.getProductId(),
                    cartItem.getQty(),
                    cartItem.getUnitPriceSnapshot()
            );
            orderItemRepository.save(orderItem);
        }

        OrderHistory history = OrderHistory.createForNew(order.getId(), OrderStatus.PENDING);
        orderHistoryRepository.save(history);

        return order;
    }

    @Transactional
    public Order processPayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));

        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new IllegalStateException("결제 대기 상태가 아닙니다.");
        }

        if (order.isExpired()) {
            order.expire();
            orderRepository.save(order);
            OrderHistory history = OrderHistory.create(order.getId(), OrderStatus.PENDING, OrderStatus.EXPIRED, "결제 시간 초과", ActorType.SYSTEM);
            orderHistoryRepository.save(history);
            throw new IllegalStateException("주문이 만료되었습니다.");
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);

        for (OrderItem item : orderItems) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + item.getProductId()));

            product.decreaseStock(item.getQty());
            productRepository.save(product);
        }

        order.markAsPaid();
        orderRepository.save(order);

        OrderHistory history = OrderHistory.create(order.getId(), OrderStatus.PENDING, OrderStatus.PAID, "결제 완료", ActorType.USER);
        orderHistoryRepository.save(history);

        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + id));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByUser(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<OrderHistory> getOrderHistory(Long orderId) {
        return orderHistoryRepository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderItem> getOrderItems(Long orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }
}
