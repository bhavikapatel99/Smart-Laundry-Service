package com.SmartLaundry.service;

import com.SmartLaundry.model.*;
import com.SmartLaundry.repository.DeliveryAgentRepository;
import com.SmartLaundry.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OtpOrderTransitionService {

    private final OrderRepository orderRepository;
    private final DeliveryAgentRepository deliveryAgentRepository;
    private final OrderOtpService orderOtpService;
    private final OrderStatusHistoryService orderStatusHistoryService;

    public void verifyPickupOtp(String orderId, String otpInput, String agentId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        Boolean needsAgent = order.getServiceProvider().getNeedOfDeliveryAgent();

        DeliveryAgent agent = null;
        if (Boolean.TRUE.equals(needsAgent)) {
            agent = deliveryAgentRepository.findById(agentId)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery agent not found"));
        }

        if (!orderOtpService.validateOtp(order, otpInput, OtpPurpose.PICKUP_CUSTOMER)) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        // Step 1: Mark as PICKED_UP
        order.setStatus(OrderStatus.PICKED_UP);
        orderRepository.save(order);
        orderStatusHistoryService.save(order, OrderStatus.PICKED_UP);

        // Step 2: Based on delivery agent need
        if (Boolean.TRUE.equals(needsAgent)) {
            // Send handover OTP to provider
            Users providerUser = order.getServiceProvider().getUser();
            orderOtpService.generateAndSendOtp(
                    order,
                    null,
                    agent,
                    OtpPurpose.HANDOVER_TO_PROVIDER,
                    providerUser.getPhoneNo()
            );
        } else {
            // No delivery agent — directly move to IN_CLEANING
            order.setStatus(OrderStatus.IN_CLEANING);
            orderRepository.save(order);
            orderStatusHistoryService.save(order, OrderStatus.IN_CLEANING);
        }
    }


    public void verifyHandoverOtp(String orderId, String otpInput, String agentId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery agent not found"));

        if (!orderOtpService.validateOtp(order, otpInput, OtpPurpose.HANDOVER_TO_PROVIDER)) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        order.setStatus(OrderStatus.IN_CLEANING);
        orderRepository.save(order);
        orderStatusHistoryService.save(order, OrderStatus.IN_CLEANING);
    }

    public void verifyDeliveryOtp(String orderId, String otpInput, String verifierId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        Boolean needsAgent = order.getServiceProvider().getNeedOfDeliveryAgent();

        if (Boolean.TRUE.equals(needsAgent)) {
            // Delivery agent case
            DeliveryAgent agent = deliveryAgentRepository.findById(verifierId)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery agent not found"));
        } else {
            // Provider is delivering, so verify the verifierId is the provider
            String providerId = order.getServiceProvider().getUser().getUserId();
            if (!providerId.equals(verifierId)) {
                throw new IllegalArgumentException("Unauthorized: only the service provider can confirm delivery for this order.");
            }
        }

        // Validate OTP sent to customer
        if (!orderOtpService.validateOtp(order, otpInput, OtpPurpose.DELIVERY_CUSTOMER)) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        // Mark order as delivered
        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);
        orderStatusHistoryService.save(order, OrderStatus.DELIVERED);
    }

    public void resendOtp(String orderId, OtpPurpose purpose) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        String phone = switch (purpose) {
            case PICKUP_CUSTOMER, DELIVERY_CUSTOMER ->
                    order.getUser().getPhoneNo();
            case HANDOVER_TO_PROVIDER ->
                    order.getServiceProvider().getUser().getPhoneNo();
            default -> throw new IllegalArgumentException("Unsupported purpose");
        };

        orderOtpService.generateAndSendOtp(order, null, null, purpose, phone);
    }

}
