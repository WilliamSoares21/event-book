package com.eventbook.backend.events;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eventbook.backend.checkin.CheckInRepository;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.orders.OrderRepository;
import com.eventbook.backend.tickets.TicketRepository;
import com.eventbook.backend.users.UserRepository;

@ExtendWith(MockitoExtension.class)
public class EventServiceTest {
    
    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketLotRepository ticketLotRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EventService eventService;
}
