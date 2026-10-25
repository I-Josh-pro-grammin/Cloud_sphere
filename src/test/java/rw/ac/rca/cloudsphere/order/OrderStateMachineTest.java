package rw.ac.rca.cloudsphere.order;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rw.ac.rca.cloudsphere.common.exception.ApiException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class OrderStateMachineTest {

    @Test
    void assertTransition_forwardPath_isLegal() {
        assertDoesNotThrow(() -> OrderStateMachine.assertTransition(OrderStatus.PLACED, OrderStatus.PAID));
        assertDoesNotThrow(() -> OrderStateMachine.assertTransition(OrderStatus.PAID, OrderStatus.SHIPPED));
        assertDoesNotThrow(() -> OrderStateMachine.assertTransition(OrderStatus.SHIPPED, OrderStatus.DELIVERED));
    }

    @Test
    void assertTransition_deliveredToPlaced_throws() {
        assertThrows(ApiException.class,
                () -> OrderStateMachine.assertTransition(OrderStatus.DELIVERED, OrderStatus.PLACED));
    }

    @Test
    void assertTransition_paidToPlaced_throws() {
        assertThrows(ApiException.class,
                () -> OrderStateMachine.assertTransition(OrderStatus.PAID, OrderStatus.PLACED));
    }

    @Test
    void assertTransition_timedOutToPaid_isLegal() {
        assertDoesNotThrow(() -> OrderStateMachine.assertTransition(OrderStatus.PAYMENT_TIMED_OUT, OrderStatus.PAID));
    }

    @Test
    void assertTransition_notYetShipped_allowsCancel() {
        assertDoesNotThrow(() -> OrderStateMachine.assertTransition(OrderStatus.PLACED, OrderStatus.CANCELLED));
        assertDoesNotThrow(() -> OrderStateMachine.assertTransition(OrderStatus.PLACED, OrderStatus.PAYMENT_TIMED_OUT));
        assertDoesNotThrow(() -> OrderStateMachine.assertTransition(OrderStatus.PAID, OrderStatus.CANCELLED));
        assertDoesNotThrow(() -> OrderStateMachine.assertTransition(OrderStatus.PAYMENT_TIMED_OUT, OrderStatus.CANCELLED));
    }

    @Test
    void assertTransition_terminalOrBackward_throws() {
        assertThrows(ApiException.class,
                () -> OrderStateMachine.assertTransition(OrderStatus.SHIPPED, OrderStatus.PAID));
        assertThrows(ApiException.class,
                () -> OrderStateMachine.assertTransition(OrderStatus.CANCELLED, OrderStatus.PAID));
        assertThrows(ApiException.class,
                () -> OrderStateMachine.assertTransition(OrderStatus.DELIVERED, OrderStatus.DELIVERED));
    }
}
