package org.afob.limit;

import org.afob.execution.ExecutionClient;
import org.afob.prices.PriceListener;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class LimitOrderAgent implements PriceListener {

    private final ExecutionClient executionClient;
    private final List<LimitOrder> limitOrders;

    public LimitOrderAgent(final ExecutionClient ec) {
        this.executionClient = ec;
        this.limitOrders = new ArrayList<>();
    }

    public synchronized void addOrder(OrderType orderType, String productId, int amount, BigDecimal limitPrice) throws OrderException {
        validateOrder(orderType, productId, amount, limitPrice);
        limitOrders.add(new LimitOrder(orderType, productId, amount, limitPrice));
    }

    private void validateOrder(OrderType orderType, String productId, int amount, BigDecimal limitPrice) throws OrderException {
        if (amount <= 0) {
            throw new OrderException("Order amount must be positive.");
        }
        if (limitPrice == null) {
            throw new OrderException("Limit price cannot be null.");
        }
    }

    @Override
    public synchronized void priceTick(String productId, BigDecimal price) {
        for (LimitOrder order : limitOrders) {
            if (order.getProductId().equals(productId) && order.isExecutable(price)) {
                executeOrder(order);
            }
        }
    }

    private synchronized void executeOrder(LimitOrder order) {
        try {
            if (order.isBuy()) {
                executionClient.buy(order.getProductId(), order.getAmount());
            } else {
                executionClient.sell(order.getProductId(), order.getAmount());
            }
            cancelOrder(order.getId());
        } catch (ExecutionException e) {
            System.err.println("Failed to execute order: " + e.getMessage());
        }
    }

    private synchronized void cancelOrder(String orderId) {
        limitOrders.removeIf(order -> order.getId().equals(orderId));
    }

    private static class LimitOrder {
        private final String id;
        private final OrderType orderType;
        private final String productId;
        private final int amount;
        private final BigDecimal limitPrice;

        public LimitOrder(OrderType orderType, String productId, int amount, BigDecimal limitPrice) {
            this.id = UUID.randomUUID().toString();
            this.orderType = orderType;
            this.productId = productId;
            this.amount = amount;
            this.limitPrice = limitPrice;
        }

        public String getId() {
            return id;
        }

        public OrderType getOrderType() {
            return orderType;
        }

        public String getProductId() {
            return productId;
        }

        public int getAmount() {
            return amount;
        }

        public BigDecimal getLimitPrice() {
            return limitPrice;
        }

        public boolean isExecutable(BigDecimal currentPrice) {
            return (orderType == OrderType.BUY && currentPrice <= limitPrice) ||
                   (orderType == OrderType.SELL && currentPrice >= limitPrice);
        }
    }

    public enum OrderType {
        BUY, SELL
    }

    public static class OrderException extends Exception {
        public OrderException(String message) {
            super(message);
        }
    }
}
