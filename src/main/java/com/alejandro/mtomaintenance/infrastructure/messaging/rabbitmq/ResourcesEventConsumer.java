package com.alejandro.mtomaintenance.infrastructure.messaging.rabbitmq;

import com.alejandro.mtomaintenance.application.dto.messaging.InboxMessageCommand;
import com.alejandro.mtomaintenance.application.dto.messaging.InboxProcessingResult;
import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEventMessage;
import com.alejandro.mtomaintenance.application.service.IncomingEventProcessor;
import com.alejandro.mtomaintenance.configuration.messaging.MessagePayloadSignatureVerifier;
import com.alejandro.mtomaintenance.infrastructure.persistence.audit.MessagingAuditContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * Lee de su cola las personas, los equipos, los vehiculos y los instrumentos de {@code mto-resources} y los entrega al
 * procesador idempotente. Es delgado a proposito, como {@link MasterDataEventConsumer}: comprueba la
 * firma, que el sobre trae lo minimo y que viene de quien dice, traduce los metadatos de AMQP a un
 * comando y delega.
 *
 * <p>Lo que no tiene arreglo (una firma que no cuadra, un sobre sin {@code data}, un emisor que no es
 * {@code mto-resources}) va a la DLQ sin reintentos con {@link AmqpRejectAndDontRequeueException}; lo
 * que lance el manejador se propaga para que el contenedor reintente. Nunca se traga una excepcion:
 * el contenedor confirmaria un evento que no se ha aplicado.</p>
 */
@RequiredArgsConstructor
public class ResourcesEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcesEventConsumer.class);

    private final IncomingEventProcessor processor;
    private final MessagePayloadSignatureVerifier signatureVerifier;

    @RabbitListener(
            queues = "${app.rabbitmq.resources.queue:" + ResourcesRabbitMqNames.MAINTENANCE_RESOURCES_QUEUE + "}",
            containerFactory = RabbitListenerContainerFactoryNames.RESOURCES)
    public void onResourcesEvent(IncomingEventMessage message, Message rawMessage) {
        MessageProperties properties = rawMessage.getMessageProperties();

        LOGGER.info("Resources event received: exchange={}, routingKey={}, queue={}, messageId={}, eventType={}, "
                        + "aggregateType={}, aggregateId={}, sequenceNumber={}",
                properties.getReceivedExchange(), properties.getReceivedRoutingKey(), properties.getConsumerQueue(),
                properties.getMessageId(), properties.getHeader(MasterDataMessageHeaders.EVENT_TYPE),
                properties.getHeader(MasterDataMessageHeaders.AGGREGATE_TYPE), properties.getHeader(MasterDataMessageHeaders.AGGREGATE_ID),
                properties.getHeader(MasterDataMessageHeaders.SEQUENCE_NUMBER));

        rejectIfNotAuthentic(rawMessage, properties);
        rejectIfUnprocessable(message, properties);

        InboxMessageCommand command = InboxMessageCommandFactory.from(message, rawMessage);

        MessagingAuditContext.set(new MessagingAuditContext.Context(command.messageId(), command.sourceService()));
        InboxProcessingResult result;
        try {
            result = processor.process(command, message);
        } finally {
            MessagingAuditContext.clear();
        }

        LOGGER.info("Resources event settled: messageId={}, idempotencyKey={}, result={}",
                properties.getMessageId(), command.messageId(), result);
    }

    private void rejectIfNotAuthentic(Message rawMessage, MessageProperties properties) {
        signatureVerifier
                .rejectionReason(rawMessage.getBody(),
                        header(properties, MasterDataMessageHeaders.SIGNATURE),
                        header(properties, MasterDataMessageHeaders.SIGNATURE_ALGORITHM))
                .ifPresent(reason -> {
                    throw unprocessable("was rejected because " + reason, properties);
                });
    }

    private static void rejectIfUnprocessable(IncomingEventMessage message, MessageProperties properties) {
        if (message == null || message.data() == null) {
            throw unprocessable("has no 'data' payload", properties);
        }
        if (!ResourcesRabbitMqNames.RESOURCES_SOURCE_SERVICE.equals(message.origin())) {
            throw unprocessable("comes from '" + message.origin() + "', not from mto-resources", properties);
        }
        if (message.data().entityName() == null || message.data().entityName().isBlank()) {
            throw unprocessable("has no entityName", properties);
        }
        if (message.data().eventName() == null || message.data().eventName().isBlank()) {
            throw unprocessable("has no eventName", properties);
        }
    }

    private static String header(MessageProperties properties, String name) {
        Object value = properties.getHeader(name);
        return value == null ? null : value.toString();
    }

    private static AmqpRejectAndDontRequeueException unprocessable(String reason, MessageProperties properties) {
        LOGGER.error("Resources event sent to the dead letter queue, it {}: messageId={}, exchange={}, routingKey={}",
                reason, properties.getMessageId(), properties.getReceivedExchange(), properties.getReceivedRoutingKey());
        return new AmqpRejectAndDontRequeueException("Resources event " + reason + ": messageId=" + properties.getMessageId());
    }
}
