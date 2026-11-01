package com.eventbook.backend.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor 
public class EventService {

    private final EventRepository eventRepository;

    /**
     * Cria um novo evento salvando-o no banco de dados.
     */
    @Transactional
    public EventResponseDTO create(EventResponseDTO dto) {
        // Converte o DTO para a Entidade Event
        Event event = Event.builder()
                .title(dto.title())
                .description(dto.description())
                .date(dto.date())
                .location(dto.location())
                .status(EventStatus.PENDING) // Exemplo de uso do seu EventStatus
                .build();
        
        Event savedEvent = eventRepository.save(event);
        
        // Retorna o DTO de resposta preenchido
        return toResponseDTO(savedEvent);
    }

    /**
     * Busca um evento específico pelo ID.
     */
    @Transactional(readOnly = true)
    public EventResponseDTO getById(UUID id) {
        return eventRepository.findById(id)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new RuntimeException("Evento não encontrado com o ID: " + id));
    }

    /**
     * Lista todos os eventos cadastrados.
     */
    @Transactional(readOnly = true)
    public List<EventResponseDTO> listAll() {
        return eventRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    /**
     * Atualiza um evento existente utilizando a nomenclatura EventUpdateDTO.
     */
    @Transactional
    public EventResponseDTO update(UUID id, EventUpdateDTO dto) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Evento não encontrado para atualização"));

        // Atualiza os campos permitidos vindos do EventUpdateDTO
        event.setTitle(dto.title());
        event.setDescription(dto.description());
        event.setDate(dto.date());
        event.setLocation(dto.location());
        
        Event updatedEvent = eventRepository.save(event);
        return toResponseDTO(updatedEvent);
    }

    /**
     * Remove um evento do sistema.
     */
    @Transactional
    public void delete(UUID id) {
        if (!eventRepository.existsById(id)) {
            throw new RuntimeException("Não foi possível deletar. Evento não encontrado.");
        }
        eventRepository.deleteById(id);
    }

    /**
     * Método utilitário de conversão (substituindo o Mapper ausente no pacote)
     */
    private EventResponseDTO toResponseDTO(Event event) {
        return new EventResponseDTO(
            event.getId(),
            event.getTitle(),
            event.getDescription(),
            event.getDate(),
            event.getLocation(),
            event.getStatus()
        );
    }
}
