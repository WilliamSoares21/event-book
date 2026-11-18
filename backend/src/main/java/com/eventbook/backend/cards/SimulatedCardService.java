package com.eventbook.backend.cards;

import com.eventbook.backend.cards.dto.CardResponseDTO;
import com.eventbook.backend.cards.exception.CardAccessDeniedException;
import com.eventbook.backend.cards.exception.CardNotFoundException;
import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimulatedCardService {

    private static final String[] BRANDS = {"VISA", "MASTERCARD", "ELO"};

    private final SimulatedCardRepository simulatedCardRepository;
    private final UserRepository userRepository;

    @Value("${cards.default-limit:1000.00}")
    private BigDecimal defaultLimit;

    @Transactional
    public CardResponseDTO createCard(UUID userId) {
        // Bloqueia a linha do usuário: serializa criação/substituição de cartão por usuário.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CardAccessDeniedException("Usuário não encontrado"));

        if (simulatedCardRepository.existsByUserAndStatus(user, CardStatus.ACTIVE)) {
            throw new BusinessException(
                    "Usuário já possui um cartão ativo. Desative-o antes de cadastrar outro.",
                    HttpStatus.CONFLICT);
        }

        // Regra F2: herda o limite disponível do cartão mais recente; só o primeiro cartão recebe o limite inicial.
        BigDecimal limit = simulatedCardRepository.findFirstByUserOrderByCreatedAtDesc(user)
                .map(SimulatedCard::getAvailableLimit)
                .orElse(defaultLimit);

        SimulatedCard card = SimulatedCard.builder()
                .user(user)
                .lastFourDigits(generateLastFourDigits(user))
                .brand(BRANDS[Math.floorMod(user.getId().hashCode(), BRANDS.length)])
                .expiryMonth(Math.floorMod(user.getId().hashCode(), 12) + 1)
                .expiryYear(java.time.Year.now().getValue() + 3)
                .totalLimit(limit)
                .availableLimit(limit)
                .status(CardStatus.ACTIVE)
                .build();

        return CardResponseDTO.from(simulatedCardRepository.save(card));
    }

    @Transactional(readOnly = true)
    public List<CardResponseDTO> listMyCards(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CardAccessDeniedException("Usuário não encontrado"));

        return simulatedCardRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(CardResponseDTO::from)
                .toList();
    }

    @Transactional
    public void deactivateCard(UUID userId, UUID cardId) {
        SimulatedCard card = simulatedCardRepository.findById(cardId)
                .orElseThrow(() -> new CardNotFoundException("Cartão não encontrado: " + cardId));

        if (!card.getUser().getId().equals(userId)) {
            throw new CardAccessDeniedException("Acesso negado: cartão não pertence ao usuário");
        }

        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new BusinessException("Cartão já está inativo", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        card.setStatus(CardStatus.INACTIVE);
        simulatedCardRepository.save(card);
    }

    // Dados fictícios determinísticos por usuário: mesmos valores em toda execução (testável).
    private String generateLastFourDigits(User user) {
        int value = Math.floorMod(user.getId().hashCode() ^ user.getEmail().hashCode(), 10000);
        return String.format("%04d", value);
    }
}