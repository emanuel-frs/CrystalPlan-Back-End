package com.project.crystalplan.application.impl;

import com.project.crystalplan.domain.enums.NotificationType;
import com.project.crystalplan.domain.enums.Recurrence;
import com.project.crystalplan.domain.exceptions.EntityNotFoundException;
import com.project.crystalplan.domain.exceptions.InvalidArgumentException;
import com.project.crystalplan.domain.exceptions.InvalidCredentialsException;
import com.project.crystalplan.domain.models.Event;
import com.project.crystalplan.domain.models.User;
import com.project.crystalplan.domain.repositories.UserRepository;
import com.project.crystalplan.domain.services.EventService;
import com.project.crystalplan.domain.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final EventService eventService;
    private final BCryptPasswordEncoder passwordEncoder;

    private static final Pattern EMAIL_REGEX =
            Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$");

    private static final Pattern PASSWORD_REGEX =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&]).{8,}$");

    @Override
    public User createUser(User user) {
        validateUser(user);

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new InvalidArgumentException("Já existe um usuário com este e-mail.");
        }

        // Criptografar a senha antes de salvar
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        user.setUuid(UUID.randomUUID().toString());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setActive(true);

        User createdUser = userRepository.save(user);
        generateBirthdayEvents(createdUser);

        return createdUser;
    }

    private void validateUser(User user) {
        if (user.getName() == null || user.getName().trim().isEmpty()) {
            throw new InvalidArgumentException("O nome é obrigatório.");
        }
        if (user.getEmail() == null || !EMAIL_REGEX.matcher(user.getEmail()).matches()) {
            throw new InvalidArgumentException("E-mail inválido ou não informado.");
        }
        if (user.getPassword() == null || !PASSWORD_REGEX.matcher(user.getPassword()).matches()) {
            throw new InvalidArgumentException("A senha deve ter pelo menos 8 caracteres, incluindo letra, número e caractere especial.");
        }
        if (user.getBirthday() == null) {
            throw new InvalidArgumentException("Data de nascimento é obrigatória.");
        }
    }

    private void generateBirthdayEvents(User user) {
        LocalDate birthday = user.getBirthday();
        int currentYear = LocalDate.now().getYear();
        List<Event> events = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            int targetYear = currentYear + i;
            LocalDate eventDate = birthday.withYear(targetYear);

            if (i == 0 && eventDate.isBefore(LocalDate.now())) {
                eventDate = eventDate.plusYears(1);
                targetYear++;
            }

            int age = targetYear - birthday.getYear();
            String description = String.format(
                    "Feliz Aniversário, %s!!! É um dia muito importante, espero que aproveite seu dia! Parabéns pelo seu aniversário de %d anos.",
                    user.getName(), age
            );

            Event event = new Event(
                    null,
                    "Seu aniversário!!",
                    description,
                    Recurrence.SINGLE,
                    eventDate,
                    null,
                    LocalTime.of(10, 0),
                    null,
                    true,
                    NotificationType.EMAIL,
                    user.getId()
            );

            events.add(event);
        }

        events.forEach(eventService::createEvent);
    }

    @Override
    public Optional<User> getUserById(String id) {
        return userRepository.findByIdAndActiveTrue(id);
    }

    @Override
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmailAndActiveTrue(email);
    }

    @Override
    public User updateUser(String id, User user) {
        validateUser(user);

        User existing = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado para atualização ou inativo."));

        existing.setName(user.getName());
        existing.setEmail(user.getEmail());

        // Criptografar a nova senha se foi alterada
        if (!passwordEncoder.matches(user.getPassword(), existing.getPassword())) {
            existing.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        existing.setBirthday(user.getBirthday());
        existing.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(existing);
    }

    @Override
    public void deleteUser(String id) {
        User userToDelete = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado para exclusão ou já inativo."));

        userToDelete.setActive(false);
        userToDelete.setUpdatedAt(LocalDateTime.now());
        userRepository.save(userToDelete);
    }

    @Override
    public Optional<User> login(String email, String password) {
        Optional<User> userOptional = userRepository.findByEmailAndActiveTrue(email);

        if (userOptional.isEmpty()) {
            return Optional.empty();
        }

        User user = userOptional.get();

        // Usar BCrypt para verificar a senha
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Senha inválida.");
        }

        return Optional.of(user);
    }
}