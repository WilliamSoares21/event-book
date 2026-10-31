package com.eventbook.backend.events;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

  private final EventService eventService;

  public EventController(EventService eventService) {
    this.eventService = eventService;
  }


  // Rota pública liberada pelo SecurityConfig, porque o professor deu a dica de deixar publica 
  @GetMapping
  public ResponseEntity<List<EventResponseDTO>> listEvents() {
      List<EventResponseDTO> events = eventService.findAllActiveEvents();
      return ResponseEntity.ok(events);
  }

  //Rota privada (exige token JWT, porque pra postar tem que tá logado)
  //só cria evento quem é Organizador precisamos criar a logica 
  @PostMapping
  public ResponseEntity<EventResponseDTO> createEvent(@RequestBody EventRequestDTO dto) {
      EventResponseDTO newEvent = eventService.createNewEvent(dto);
      return ResponseEntity.ok(newEvent);
  }
  // Precisa ser ajustado para seguir as regras de negocio do projeto
  @PatchMapping("/{id}")
  public ResponseEntity<EventResponseDTO> updateEvent(@PathVariable Long id, @RequestBody EventUpdateDTO dto) {
      EventResponseDTO updatedEvent = eventService.updateEvent(id, dto);
      return ResponseEntity.ok(updatedEvent);
  }


  // precisamoa ajustarar para apenas o organizador conseguir publicar evento
  @PostMapping("/{id}/publish")
  public ResponseEntity<Void> publishEvent(@PathVariable Long id) {
      eventService.publish(id); // Faz as validações da regra 01 e muda status
      return ResponseEntity.noContent().build(); // Retorna 204
  }

}
