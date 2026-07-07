package com.smartcharging.stationprovider.config;

import com.smartcharging.stationprovider.endpoint.StationPortTypeImpl;
import jakarta.xml.ws.Endpoint;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configurazione del bus Apache CXF per l'esposizione degli endpoint SOAP.
 * <p>Sostituisce il meccanismo automatico dei @RestController. Registra
 * manualmente l'implementazione del servizio nel Bus CXF, rendendola
 * accessibile all'URL specificato e generando automaticamente il file WSDL.</p>
 */
@Configuration
public class CxfConfig {

    private final Bus bus;
    private final StationPortTypeImpl stationPortTypeImpl;

    // Iniezione via costruttore (regola Clean Code)
    public CxfConfig(Bus bus, StationPortTypeImpl stationPortTypeImpl) {
        this.bus = bus;
        this.stationPortTypeImpl = stationPortTypeImpl;
    }

    @Bean
    public Endpoint stationEndpoint() {
        EndpointImpl endpoint = new EndpointImpl(bus, stationPortTypeImpl);
        // L'endpoint finale sarà: http://<host>:8083/ws/station
        endpoint.publish("/station"); 
        return endpoint;
    }
}