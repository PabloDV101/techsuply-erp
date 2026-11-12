package com.techsupply.erp_backend.inventario;

import com.techsupply.erp_backend.inventario.application.AlertaStockService;
import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AlertaStockIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http; @Autowired JdbcTemplate db; @Autowired ObjectMapper json; @Autowired PasswordEncoder passwords;
    @Autowired AlertaStockService service;
    record Account(String token,long id) {}
    private long id(String sql,Object... args) {return db.queryForObject(sql,Long.class,args);}
    private Account account(String permission) throws Exception {
        String key=UUID.randomUUID().toString(),password=UUID.randomUUID().toString();
        long role=id("insert into roles(codigo,nombre) values (?,?) returning id","T_"+key.substring(0,20).toUpperCase(Locale.ROOT),"Test");
        db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?",role,permission);
        long user=id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",key,key+"@example.test",passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)",user,role);
        String body=http.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identificador",key,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Account(json.readTree(body).get("accessToken").asText(),user);
    }
    private long product(String key,long category,String threshold,String reorder,boolean active) {return id("insert into productos(sku,nombre,categoria_id,punto_reorden,cantidad_reorden,activo) values (?,?,?,?,?,?) returning id",key,"Producto "+key,category,new BigDecimal(threshold),new BigDecimal(reorder),active);}
    private void stock(long product,long warehouse,String physical,String reserved) {db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica,cantidad_reservada) values (?,?,?,?)",product,warehouse,new BigDecimal(physical),new BigDecimal(reserved));}
    private JsonNode fetch(Account user,long product,int page,int size) throws Exception {
        return json.readTree(http.perform(get("/api/v1/inventory/low-stock-alerts").header("Authorization","Bearer "+user.token()).param("productoId",Long.toString(product)).param("page",Integer.toString(page)).param("size",Integer.toString(size))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private void alert(Account user,long product,String expected,String threshold,String reorder) throws Exception {
        JsonNode result=fetch(user,product,0,20); assertThat(result.get("totalElements").asLong()).isEqualTo(1);
        JsonNode item=result.get("content").get(0);assertThat(item.get("productoId").asLong()).isEqualTo(product);
        assertThat(item.get("disponibleGlobal").decimalValue()).isEqualByComparingTo(expected);assertThat(item.get("puntoReorden").decimalValue()).isEqualByComparingTo(threshold);assertThat(item.get("cantidadReorden").decimalValue()).isEqualByComparingTo(reorder);
        assertThat(item.get("sku").asText()).isNotBlank();assertThat(item.get("nombre").asText()).isNotBlank();
    }
    private void noAlert(Account user,long product) throws Exception {JsonNode r=fetch(user,product,0,20);assertThat(r.get("totalElements").asLong()).isZero();assertThat(r.get("content").size()).isZero();assertThat(r.get("totalPages").asInt()).isZero();}
    @Test void consultaUmbralGlobalPaginacionPermisosYNoProduceEfectos() throws Exception {
        Account reader=account("INVENTARIO_ALERTAS_LEER"),denied=account("INVENTARIO_EXISTENCIAS_LEER");
        String key="L"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long category=id("insert into categorias_producto(codigo,nombre) values (?,?) returning id",key,"Prueba");
        long active=id("insert into almacenes(codigo,nombre,activo) values (?,?,true) returning id",key+"A","Activo");
        long active2=id("insert into almacenes(codigo,nombre,activo) values (?,?,true) returning id",key+"B","Activo");
        long inactive=id("insert into almacenes(codigo,nombre,activo) values (?,?,false) returning id",key+"C","Inactivo");
        long none=product(key+"N",category,"10","3",true);
        long exact=product(key+"E",category,"10","4",true);stock(exact,active,"10","2");stock(exact,active2,"2","0");stock(exact,inactive,"100","0");
        long below=product(key+"B",category,"10","0",true);stock(below,active,"9.999","0");
        long above=product(key+"S",category,"10","2",true);stock(above,active,"10.001","0");
        long zero=product(key+"Z",category,"0","2",true), inactiveProduct=product(key+"I",category,"10","2",false);
        long inactiveOnly=product(key+"W",category,"10","7",true);stock(inactiveOnly,inactive,"100","0");
        long fullyReserved=product(key+"R",category,"1","5",true);stock(fullyReserved,active,"7","7");
        long big=product(key+"G",category,"99999999999.999","1",true);stock(big,active,"99999999999.999","0");stock(big,active2,"99999999999.999","0");
        long movements=id("select count(*) from movimientos_inventario"),orders=id("select count(*) from ordenes_compra"),stocks=id("select count(*) from existencias");
        http.perform(get("/api/v1/inventory/low-stock-alerts")).andExpect(status().isUnauthorized());
        long before=id("select count(*) from eventos_auditoria where accion='AUTHORIZATION_DENIED' and usuario_id=?",denied.id());
        http.perform(get("/api/v1/inventory/low-stock-alerts").header("Authorization","Bearer "+denied.token())).andExpect(status().isForbidden());
        assertThat(id("select count(*) from eventos_auditoria where accion='AUTHORIZATION_DENIED' and usuario_id=?",denied.id())).isEqualTo(before+1);
        alert(reader,none,"0","10","3");alert(reader,exact,"10","10","4");alert(reader,below,"9.999","10","0");alert(reader,inactiveOnly,"0","10","7");alert(reader,fullyReserved,"0","1","5");
        noAlert(reader,above);noAlert(reader,zero);noAlert(reader,inactiveProduct);noAlert(reader,big);noAlert(reader,Long.MAX_VALUE);
        db.update("update existencias set cantidad_fisica=10,version=version+1 where producto_id=? and almacen_id=?",above,active);
        alert(reader,above,"10","10","2");
        db.update("update existencias set cantidad_fisica=11,version=version+1 where producto_id=? and almacen_id=?",above,active);
        noAlert(reader,above);
        JsonNode out=fetch(reader,exact,5,1);assertThat(out.get("content").size()).isZero();assertThat(out.get("totalElements").asLong()).isEqualTo(1);assertThat(out.get("totalPages").asInt()).isEqualTo(1);
        var first=json.readTree(http.perform(get("/api/v1/inventory/low-stock-alerts").header("Authorization","Bearer "+reader.token()).param("size","1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var second=json.readTree(http.perform(get("/api/v1/inventory/low-stock-alerts").header("Authorization","Bearer "+reader.token()).param("size","1").param("page","1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(first.get("totalElements").asLong()).isGreaterThanOrEqualTo(4);assertThat(second.get("totalElements").asLong()).isEqualTo(first.get("totalElements").asLong());
        assertThat(first.get("content").get(0).get("productoId").asLong()).isLessThan(second.get("content").get(0).get("productoId").asLong());
        for(var params:List.of(Map.of("page","-1"),Map.of("size","0"),Map.of("size","101"),Map.of("productoId","0"),Map.of("productoId","abc"))) {var req=get("/api/v1/inventory/low-stock-alerts").header("Authorization","Bearer "+reader.token());params.forEach(req::param);http.perform(req).andExpect(status().isBadRequest());}
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test",null,List.of()));
        try {assertThatThrownBy(()->service.listar(0,20,null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);} finally {SecurityContextHolder.clearContext();}
        assertThat(id("select count(*) from movimientos_inventario")).isEqualTo(movements);assertThat(id("select count(*) from ordenes_compra")).isEqualTo(orders);assertThat(id("select count(*) from existencias")).isEqualTo(stocks);
    }
}
