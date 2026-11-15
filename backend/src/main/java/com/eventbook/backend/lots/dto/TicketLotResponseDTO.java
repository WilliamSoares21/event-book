package com.eventbook.backend.lots.dto;

import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketLotResponseDTO {

    private UUID id;
    private UUID eventId;
    private String name;
    private BigDecimal unitPrice;
    private Integer capacity;
    private Integer sold;
    private Integer reserved;
    private Instant salesStart;
    private Instant salesEnd;
    private LotStatus status;
    private Long version;

    public static TicketLotResponseDTO from(TicketLot lot) {
        return TicketLotResponseDTO.builder()
                .id(lot.getId())
                .eventId(lot.getEvent().getId())
                .name(lot.getName())
                .unitPrice(lot.getUnitPrice())
                .capacity(lot.getCapacity())
                .sold(lot.getSold())
                .reserved(lot.getReserved())
                .salesStart(lot.getSalesStart())
                .salesEnd(lot.getSalesEnd())
                .status(lot.getStatus())
                .version(lot.getVersion())
                .build();
    }
}
