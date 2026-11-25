package com.techsupply.erp_backend.maestros.application;

import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.dto.AlmacenRequest;
import com.techsupply.erp_backend.maestros.application.dto.AlmacenResponse;
import com.techsupply.erp_backend.maestros.application.dto.ClienteRequest;
import com.techsupply.erp_backend.maestros.application.dto.ClienteResponse;
import com.techsupply.erp_backend.maestros.application.dto.ProductoRequest;
import com.techsupply.erp_backend.maestros.application.dto.ProductoResponse;
import com.techsupply.erp_backend.maestros.application.dto.ProveedorRequest;
import com.techsupply.erp_backend.maestros.application.dto.ProveedorResponse;
import com.techsupply.erp_backend.maestros.domain.model.Almacen;
import com.techsupply.erp_backend.maestros.domain.model.Cliente;
import com.techsupply.erp_backend.maestros.domain.model.CategoriaProducto;
import com.techsupply.erp_backend.maestros.domain.model.Producto;
import com.techsupply.erp_backend.maestros.domain.model.Proveedor;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.AlmacenRepository;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.CategoriaProductoRepository;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.ClienteRepository;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.ProductoRepository;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.ProveedorRepository;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MaestroOperativoService {
    private static final Map<String,String> SORT = Map.of("id","id","codigo","codigo");
    private final ProveedorRepository proveedores;
    private final ProductoRepository productos;
    private final ClienteRepository clientes;
    private final AlmacenRepository almacenes;
    private final CategoriaProductoRepository categorias;

    public MaestroOperativoService(ProveedorRepository proveedores, ProductoRepository productos,
            ClienteRepository clientes, AlmacenRepository almacenes, CategoriaProductoRepository categorias) {
        this.proveedores=proveedores; this.productos=productos; this.clientes=clientes;
        this.almacenes=almacenes; this.categorias=categorias;
    }

    @Transactional(readOnly=true)
    public PageResponse<ProveedorResponse> listarProveedores(int page,int size,String sort,String direction,String search,Boolean activo) {
        return PageResponse.from(proveedores.search(normalizarBusqueda(search),activo,
            PageRequests.of(page,size,sort,direction,SORT,"id")), MaestroOperativoService::proveedorResponse);
    }
    @Transactional public ProveedorResponse crearProveedor(ProveedorRequest r) {
        String codigo=r.codigo().trim().toUpperCase(Locale.ROOT), ruc=r.ruc().trim();
        if(proveedores.existsByCodigoIgnoreCase(codigo)||proveedores.existsByRucIgnoreCase(ruc)) throw conflicto("Proveedor");
        Proveedor p=new Proveedor(); p.setCodigo(codigo); p.setRuc(ruc); p.setRazonSocial(r.razonSocial().trim());
        p.setContacto(v(r.contacto())); p.setTelefono(v(r.telefono())); p.setEmail(v(r.email())); p.setDireccion(v(r.direccion()));
        return proveedorResponse(proveedores.saveAndFlush(p));
    }
    @Transactional public ProveedorResponse actualizarProveedor(Long id,ProveedorRequest r) {
        Proveedor p=buscarProveedor(id); String codigo=r.codigo().trim().toUpperCase(Locale.ROOT), ruc=r.ruc().trim();
        if(proveedores.existsByCodigoIgnoreCaseAndIdNot(codigo,id)||proveedores.existsByRucIgnoreCaseAndIdNot(ruc,id)) throw conflicto("Proveedor");
        p.setCodigo(codigo); p.setRuc(ruc); p.setRazonSocial(r.razonSocial().trim());
        p.setContacto(v(r.contacto())); p.setTelefono(v(r.telefono())); p.setEmail(v(r.email())); p.setDireccion(v(r.direccion()));
        return proveedorResponse(proveedores.saveAndFlush(p));
    }
    @Transactional public ProveedorResponse estadoProveedor(Long id,boolean activo){Proveedor p=buscarProveedor(id);p.setActivo(activo);return proveedorResponse(proveedores.saveAndFlush(p));}
    private Proveedor buscarProveedor(Long id){return proveedores.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Proveedor no encontrado"));}
    private static ProveedorResponse proveedorResponse(Proveedor p){return new ProveedorResponse(p.getId(),p.getCodigo(),p.getRuc(),p.getRazonSocial(),p.getContacto(),p.getTelefono(),p.getEmail(),p.getDireccion(),p.isActivo());}

    @Transactional(readOnly=true)
    public PageResponse<ProductoResponse> listarProductos(int page,int size,String sort,String direction,String search,Boolean activo) {
        return PageResponse.from(productos.search(normalizarBusqueda(search),activo,
            PageRequests.of(page,size,sort,direction,SORT,"id")), MaestroOperativoService::productoResponse);
    }
    @Transactional public ProductoResponse crearProducto(ProductoRequest r) {
        String codigo=r.codigo().trim().toUpperCase(Locale.ROOT);
        if(productos.existsByCodigoIgnoreCase(codigo)) throw conflicto("Producto");
        CategoriaProducto categoria=categorias.findById(r.categoriaId()).filter(CategoriaProducto::isActiva)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Categoría no encontrada o inactiva"));
        Producto p=new Producto(); p.setCodigo(codigo); p.setDescripcion(r.descripcion().trim()); p.setUnidadMedida(r.unidadMedida().trim().toUpperCase(Locale.ROOT));
        p.setCategoria(categoria); p.setPrecioReferencia(nonNegative(r.precioReferencia(),"precio")); return productoResponse(productos.saveAndFlush(p));
    }
    @Transactional public ProductoResponse actualizarProducto(Long id,ProductoRequest r) {
        Producto p=productos.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Producto no encontrado"));
        String codigo=r.codigo().trim().toUpperCase(Locale.ROOT);
        if(productos.existsByCodigoIgnoreCaseAndIdNot(codigo,id)) throw conflicto("Producto");
        CategoriaProducto categoria=categorias.findById(r.categoriaId()).filter(CategoriaProducto::isActiva)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Categoría no encontrada o inactiva"));
        p.setCodigo(codigo); p.setDescripcion(r.descripcion().trim()); p.setUnidadMedida(r.unidadMedida().trim().toUpperCase(Locale.ROOT));
        p.setCategoria(categoria); p.setPrecioReferencia(nonNegative(r.precioReferencia(),"precio")); return productoResponse(productos.saveAndFlush(p));
    }
    @Transactional public ProductoResponse estadoProducto(Long id,boolean activo){Producto p=productos.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Producto no encontrado"));p.setActivo(activo);return productoResponse(productos.saveAndFlush(p));}
    private static ProductoResponse productoResponse(Producto p){return new ProductoResponse(p.getId(),p.getCodigo(),p.getCategoria()==null?null:p.getCategoria().getId(),p.getCategoria()==null?null:p.getCategoria().getNombre(),p.getDescripcion(),p.getUnidadMedida(),p.getPrecioReferencia(),p.isActivo());}

    @Transactional(readOnly=true)
    public PageResponse<ClienteResponse> listarClientes(int page,int size,String sort,String direction,String search,Boolean activo) {
        return PageResponse.from(clientes.search(normalizarBusqueda(search),activo,PageRequests.of(page,size,sort,direction,SORT,"id")),MaestroOperativoService::clienteResponse);
    }
    @Transactional public ClienteResponse crearCliente(ClienteRequest r) {String codigo=r.codigo().trim().toUpperCase(Locale.ROOT),ruc=r.ruc().trim();if(clientes.existsByCodigoIgnoreCase(codigo)||clientes.existsByRucIgnoreCase(ruc))throw conflicto("Cliente");Cliente c=new Cliente();c.setCodigo(codigo);c.setRuc(ruc);c.setRazonSocial(r.razonSocial().trim());c.setContacto(v(r.contacto()));c.setTelefono(v(r.telefono()));c.setEmail(v(r.email()));c.setDireccion(v(r.direccion()));c.setLimiteCredito(nonNegative(r.limiteCredito(),"límite"));c.setPlazoPagoDias(r.plazoPagoDias());return clienteResponse(clientes.saveAndFlush(c));}
    @Transactional public ClienteResponse actualizarCliente(Long id,ClienteRequest r){Cliente c=clientes.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cliente no encontrado"));String codigo=r.codigo().trim().toUpperCase(Locale.ROOT),ruc=r.ruc().trim();if(clientes.existsByCodigoIgnoreCaseAndIdNot(codigo,id)||clientes.existsByRucIgnoreCaseAndIdNot(ruc,id))throw conflicto("Cliente");c.setCodigo(codigo);c.setRuc(ruc);c.setRazonSocial(r.razonSocial().trim());c.setContacto(v(r.contacto()));c.setTelefono(v(r.telefono()));c.setEmail(v(r.email()));c.setDireccion(v(r.direccion()));c.setLimiteCredito(nonNegative(r.limiteCredito(),"límite"));c.setPlazoPagoDias(r.plazoPagoDias());return clienteResponse(clientes.saveAndFlush(c));}
    @Transactional public ClienteResponse estadoCliente(Long id,boolean activo){Cliente c=clientes.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cliente no encontrado"));c.setActivo(activo);return clienteResponse(clientes.saveAndFlush(c));}
    private static ClienteResponse clienteResponse(Cliente c){return new ClienteResponse(c.getId(),c.getCodigo(),c.getRuc(),c.getRazonSocial(),c.getContacto(),c.getTelefono(),c.getEmail(),c.getDireccion(),c.getLimiteCredito(),c.getPlazoPagoDias(),c.isActivo());}

    @Transactional(readOnly=true)
    public PageResponse<AlmacenResponse> listarAlmacenes(int page,int size,String sort,String direction,String search,Boolean activo){return PageResponse.from(almacenes.search(normalizarBusqueda(search),activo,PageRequests.of(page,size,sort,direction,SORT,"id")),MaestroOperativoService::almacenResponse);}
    @Transactional public AlmacenResponse crearAlmacen(AlmacenRequest r){String codigo=r.codigo().trim().toUpperCase(Locale.ROOT);if(almacenes.existsByCodigoIgnoreCase(codigo))throw conflicto("Almacén");Almacen a=new Almacen();a.setCodigo(codigo);a.setNombre(r.nombre().trim());a.setSede(v(r.sede()));a.setUbicacion(v(r.ubicacion()));return almacenResponse(almacenes.saveAndFlush(a));}
    @Transactional public AlmacenResponse actualizarAlmacen(Long id,AlmacenRequest r){Almacen a=almacenes.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Almacén no encontrado"));String codigo=r.codigo().trim().toUpperCase(Locale.ROOT);if(almacenes.existsByCodigoIgnoreCaseAndIdNot(codigo,id))throw conflicto("Almacén");a.setCodigo(codigo);a.setNombre(r.nombre().trim());a.setSede(v(r.sede()));a.setUbicacion(v(r.ubicacion()));return almacenResponse(almacenes.saveAndFlush(a));}
    @Transactional public AlmacenResponse estadoAlmacen(Long id,boolean activo){Almacen a=almacenes.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Almacén no encontrado"));a.setActivo(activo);return almacenResponse(almacenes.saveAndFlush(a));}
    private static AlmacenResponse almacenResponse(Almacen a){return new AlmacenResponse(a.getId(),a.getCodigo(),a.getNombre(),a.getSede(),a.getUbicacion(),a.isActivo());}

    private static String normalizarBusqueda(String search){if(search==null||search.trim().isEmpty())return null;return search.trim();}
    private static String v(String value){if(value==null||value.trim().isEmpty())return null;return value.trim();}
    private static BigDecimal nonNegative(BigDecimal value,String label){if(value==null||value.signum()<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El "+label+" no puede ser negativo");return value;}
    private static ResponseStatusException conflicto(String recurso){return new ResponseStatusException(HttpStatus.CONFLICT,recurso+" ya existe");}
}
