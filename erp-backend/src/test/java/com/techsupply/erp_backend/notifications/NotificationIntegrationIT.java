package com.techsupply.erp_backend.notifications;

import com.techsupply.erp_backend.notifications.application.NotificationPublisher;
import com.techsupply.erp_backend.notifications.application.NotificationRequest;
import com.techsupply.erp_backend.notifications.application.NotificationType;
import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired NotificationPublisher publisher;
    @Autowired PlatformTransactionManager manager;

    private record Account(long id, String token) {}

    private Account account() throws Exception {
        String name = UUID.randomUUID().toString();
        String password = UUID.randomUUID().toString();
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",
            Long.class, name, name + "@example.test", passwords.encode(password));
        String body = http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("identificador", name, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Account(user, json.readTree(body).get("accessToken").asText());
    }

    private NotificationRequest request(String key, Long... recipients) {
        return new NotificationRequest(NotificationType.RESULTADO_OPERACION, "MAESTROS",
            "CATEGORIA_PRODUCTO", 1L, key, Set.of(recipients));
    }

    private void publish(NotificationRequest request) {
        new TransactionTemplate(manager).executeWithoutResult(status -> publisher.publishAfterCommit(request));
    }

    @Test void representativeOperationNotifiesOnlyExplicitRecipientsAfterCommit() throws Exception {
        Account first = account();
        Account second = account();
        Account unrelated = account();
        String key = UUID.randomUUID().toString();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            String code = "N" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
            db.update("insert into categorias_producto(codigo,nombre) values (?,?)", code, "Operación representativa");
            publisher.publishAfterCommit(request(key, first.id(), second.id()));
            assertThat(db.queryForObject("select count(*) from notificaciones where clave_operacion=?", Long.class, key)).isZero();
        });
        assertThat(db.queryForObject("select count(*) from notificaciones where clave_operacion=?", Long.class, key)).isEqualTo(2L);
        http.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + first.token())
            .param("leida", "false")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].tipoEvento").value("RESULTADO_OPERACION"))
            .andExpect(jsonPath("$.content[0].estado").value("NO_LEIDA"));
        http.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + second.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        http.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + unrelated.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void readStateIsIdempotentAndOtherUserCannotAccessIt() throws Exception {
        Account owner = account();
        Account other = account();
        String key = UUID.randomUUID().toString();
        publish(request(key, owner.id()));
        long id = db.queryForObject("select id from notificaciones where clave_operacion=?", Long.class, key);
        http.perform(patch("/api/v1/notifications/{id}/read", id).header("Authorization", "Bearer " + other.token()))
            .andExpect(status().isNotFound());
        assertThat(db.queryForObject("select estado from notificaciones where id=?", String.class, id)).isEqualTo("NO_LEIDA");
        http.perform(patch("/api/v1/notifications/{id}/read", id).header("Authorization", "Bearer " + owner.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("LEIDA"));
        String firstRead = db.queryForObject("select leida_en::text from notificaciones where id=?", String.class, id);
        http.perform(patch("/api/v1/notifications/{id}/read", id).header("Authorization", "Bearer " + owner.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("LEIDA"));
        assertThat(db.queryForObject("select leida_en::text from notificaciones where id=?", String.class, id)).isEqualTo(firstRead);
        http.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + owner.token())
            .param("leida", "true")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        http.perform(patch("/api/v1/notifications/{id}/read", Long.MAX_VALUE)
            .header("Authorization", "Bearer " + owner.token())).andExpect(status().isNotFound());
        http.perform(patch("/api/v1/notifications/{id}/read", id)).andExpect(status().isUnauthorized());
    }

    @Test void repeatedOperationDoesNotDuplicateOrResetReadState() throws Exception {
        Account owner = account();
        String key = UUID.randomUUID().toString();
        NotificationRequest request = request(key, owner.id());
        publish(request);
        long id = db.queryForObject("select id from notificaciones where clave_operacion=?", Long.class, key);
        http.perform(patch("/api/v1/notifications/{id}/read", id).header("Authorization", "Bearer " + owner.token()))
            .andExpect(status().isOk());
        publish(request);
        assertThat(db.queryForObject("select count(*) from notificaciones where clave_operacion=?", Long.class, key)).isEqualTo(1L);
        assertThat(db.queryForObject("select estado from notificaciones where id=?", String.class, id)).isEqualTo("LEIDA");
    }

    @Test void concurrentRepeatedOperationCreatesOnlyOneNotification() throws Exception {
        Account owner = account();
        String key = UUID.randomUUID().toString();
        NotificationRequest intent = request(key, owner.id());
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    publish(intent);
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> result : results) result.get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        assertThat(db.queryForObject("select count(*) from notificaciones where clave_operacion=?", Long.class, key)).isEqualTo(1L);
    }

    @Test void rollbackDropsNotificationAndDeliveryFailureDoesNotUndoBusinessCommit() {
        String operationKey = UUID.randomUUID().toString();
        assertThatThrownBy(() -> publisher.publishAfterCommit(request(operationKey, 1L)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            publisher.publishAfterCommit(request(operationKey, Long.MAX_VALUE));
            throw new IllegalStateException("Rollback de prueba");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(db.queryForObject("select count(*) from notificaciones where clave_operacion=?", Long.class, operationKey)).isZero();

        String code = "N" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        String failureKey = UUID.randomUUID().toString();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            db.update("insert into categorias_producto(codigo,nombre) values (?,?)", code, "Operación confirmada");
            publisher.publishAfterCommit(request(failureKey, Long.MAX_VALUE)); // FK invalid after commit.
        });
        assertThat(db.queryForObject("select count(*) from categorias_producto where codigo=?", Long.class, code)).isEqualTo(1L);
        assertThat(db.queryForObject("select count(*) from notificaciones where clave_operacion=?", Long.class, failureKey)).isZero();
    }

    @Test void inboxValidatesPagingAndNeverListsAnotherUsersRows() throws Exception {
        Account owner = account();
        Account other = account();
        String key = UUID.randomUUID().toString();
        publish(request(key, owner.id()));
        for (String query : new String[] {"size=101", "page=-1", "sort=usuarioId", "direction=sideways", "leida=yes"}) {
            http.perform(get("/api/v1/notifications?" + query).header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        http.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + owner.token())
            .param("size", "1").param("page", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        http.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + other.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        http.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }
}
