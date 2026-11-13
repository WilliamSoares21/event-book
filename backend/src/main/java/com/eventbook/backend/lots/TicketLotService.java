package com.eventbook.backend.lots;

import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventRepository;
import com.eventbook.backend.lots.dto.TicketLotRequestDTO;
import com.eventbook.backend.lots.dto.TicketLotResponseDTO;
import com.eventbook.backend.lots.dto.TicketLotUpdateDTO;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class TicketLotService {

    private final TicketLotRepository ticketLotRepository;
    private final EventRepository eventRepository;

    public TicketLotService(
            TicketLotRepository ticketLotRepository,
            EventRepository eventRepository) {
        this.ticketLotRepository = ticketLotRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public TicketLotResponseDTO create(
            UUID eventId,
            TicketLotRequestDTO dto,
            UUID organizerId) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "Evento não encontrado",
                        HttpStatus.NOT_FOUND
                ));

        verifyOwnership(event, organizerId);
        validateNumericFields(dto.getUnitPrice(), dto.getCapacity());
        validateSalesWindow(dto.getSalesStart(), dto.getSalesEnd());

        // Status e contadores são definidos pelo backend. Assim, o cliente
        // não consegue criar um lote já vendido ou manipular sua capacidade.
        TicketLot lot = TicketLot.builder()
                .event(event)
                .name(dto.getName().trim())
                .unitPrice(dto.getUnitPrice())
                .capacity(dto.getCapacity())
                .salesStart(dto.getSalesStart())
                .salesEnd(dto.getSalesEnd())
                .status(LotStatus.ACTIVE)
                .sold(0)
                .reserved(0)
                .build();

        return toResponse(ticketLotRepository.save(lot));
    }

    @Transactional
    public TicketLotResponseDTO update(
            UUID lotId,
            TicketLotUpdateDTO dto,
            UUID organizerId) {

        // O lock pessimista serializa alterações do mesmo lote. Isso evita
        // que duas atualizações concorrentes reduzam a capacidade abaixo da
        // quantidade já vendida ou reservada.
        TicketLot lot = ticketLotRepository.findByIdForUpdate(lotId)
                .orElseThrow(() -> new BusinessException(
                        "Lote não encontrado",
                        HttpStatus.NOT_FOUND
                ));

        verifyOwnership(lot.getEvent(), organizerId);

        if (dto.getName() != null) {
            if (dto.getName().isBlank()) {
                throw new BusinessException(
                        "O nome do lote não pode ficar vazio",
                        HttpStatus.UNPROCESSABLE_CONTENT
                );
            }
            lot.setName(dto.getName().trim());
        }

        if (dto.getUnitPrice() != null) {
            if (dto.getUnitPrice().signum() < 0) {
                throw new BusinessException(
                        "O preço não pode ser negativo",
                        HttpStatus.UNPROCESSABLE_CONTENT
                );
            }
            lot.setUnitPrice(dto.getUnitPrice());
        }

        if (dto.getCapacity() != null) {
            if (dto.getCapacity() <= 0) {
                throw new BusinessException(
                        "A capacidade deve ser maior que zero",
                        HttpStatus.UNPROCESSABLE_CONTENT
                );
            }
            int occupied = lot.getSold() + lot.getReserved();
            if (dto.getCapacity() < occupied) {
                throw new BusinessException(
                        "A capacidade não pode ser menor que sold + reserved",
                        HttpStatus.CONFLICT
                );
            }
            lot.setCapacity(dto.getCapacity());
        }

        Instant salesStart = dto.getSalesStart() != null
                ? dto.getSalesStart()
                : lot.getSalesStart();
        Instant salesEnd = dto.getSalesEnd() != null
                ? dto.getSalesEnd()
                : lot.getSalesEnd();
        validateSalesWindow(salesStart, salesEnd);

        if (dto.getSalesStart() != null) {
            lot.setSalesStart(dto.getSalesStart());
        }
        if (dto.getSalesEnd() != null) {
            lot.setSalesEnd(dto.getSalesEnd());
        }
        if (dto.getStatus() != null) {
            lot.setStatus(dto.getStatus());
        }

        try {
            return toResponse(ticketLotRepository.save(lot));
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new BusinessException(
                    "O lote foi alterado por outro usuário",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void verifyOwnership(Event event, UUID organizerId) {
        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new BusinessException(
                    "Somente o organizador proprietário pode administrar o lote",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void validateNumericFields(BigDecimal unitPrice, Integer capacity) {
        if (unitPrice.signum() < 0) {
            throw new BusinessException(
                    "O preço não pode ser negativo",
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
        if (capacity <= 0) {
            throw new BusinessException(
                    "A capacidade deve ser maior que zero",
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
    }

    private void validateSalesWindow(Instant salesStart, Instant salesEnd) {
        if (salesStart == null || salesEnd == null || !salesEnd.isAfter(salesStart)) {
            throw new BusinessException(
                    "salesEnd deve ser posterior a salesStart",
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
    }

    private TicketLotResponseDTO toResponse(TicketLot lot) {
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
