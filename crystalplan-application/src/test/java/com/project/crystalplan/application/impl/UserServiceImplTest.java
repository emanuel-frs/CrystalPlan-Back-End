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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventService eventService;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;
    private User updatedUser;
    private User loginUser;
    private final String plainPassword = "Password@123";
    private final String encodedPassword = "$2a$10$encodedPasswordExample";

    @BeforeEach
    void setUp() {
        LocalDateTime pastTime = LocalDateTime.now().minusDays(5);

        sampleUser = new User();
        sampleUser.setId("user-id-1");
        sampleUser.setUuid(UUID.randomUUID().toString());
        sampleUser.setName("John Doe");
        sampleUser.setEmail("john.doe@example.com");
        sampleUser.setPassword(plainPassword);
        sampleUser.setBirthday(LocalDate.of(2000, 1, 1));
        sampleUser.setCreatedAt(pastTime);
        sampleUser.setUpdatedAt(pastTime);
        sampleUser.setActive(true);

        updatedUser = new User();
        updatedUser.setName("John Doe Updated");
        updatedUser.setEmail("john.doe.updated@example.com");
        updatedUser.setPassword("NewPassword@123");
        updatedUser.setBirthday(LocalDate.of(2001, 2, 2));

        loginUser = new User();
        loginUser.setId("user-id-login");
        loginUser.setUuid(UUID.randomUUID().toString());
        loginUser.setName("Login User");
        loginUser.setEmail("login@example.com");
        loginUser.setPassword(encodedPassword); // Senha já codificada no banco
        loginUser.setBirthday(LocalDate.of(1990, 1, 1));
        loginUser.setCreatedAt(pastTime.minusHours(1));
        loginUser.setUpdatedAt(pastTime.minusHours(1));
        loginUser.setActive(true);
    }

    @Test
    void createUser_ShouldSaveUserAndGenerateBirthdayEvents() {
        when(userRepository.existsByEmail(sampleUser.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(plainPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(eventService.createEvent(any(Event.class))).thenReturn(new Event());

        User createdUser = userService.createUser(sampleUser);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();

        assertNotNull(capturedUser.getUuid());
        assertNotNull(capturedUser.getCreatedAt());
        assertNotNull(capturedUser.getUpdatedAt());
        assertTrue(capturedUser.getCreatedAt().isEqual(LocalDateTime.now()) ||
                capturedUser.getCreatedAt().isAfter(sampleUser.getCreatedAt().minusSeconds(1)));
        assertTrue(capturedUser.getUpdatedAt().isEqual(LocalDateTime.now()) ||
                capturedUser.getUpdatedAt().isAfter(sampleUser.getUpdatedAt().minusSeconds(1)));
        assertTrue(capturedUser.isActive());
        assertEquals(sampleUser.getName(), capturedUser.getName());
        assertEquals(sampleUser.getEmail(), capturedUser.getEmail());

        // Verificar se a senha foi codificada
        verify(passwordEncoder).encode(plainPassword);

        // Verificar que 20 eventos de aniversário são criados
        verify(eventService, times(20)).createEvent(any(Event.class));
        assertEquals(capturedUser.getName(), createdUser.getName());
        assertEquals(capturedUser.getEmail(), createdUser.getEmail());
    }

    @Test
    void createUser_ShouldThrowInvalidArgumentExceptionWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail(sampleUser.getEmail())).thenReturn(true);

        InvalidArgumentException exception = assertThrows(InvalidArgumentException.class, () ->
                userService.createUser(sampleUser));

        assertEquals("Já existe um usuário com este e-mail.", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void createUser_ShouldThrowInvalidArgumentExceptionForInvalidName() {
        sampleUser.setName("");
        InvalidArgumentException exception = assertThrows(InvalidArgumentException.class, () ->
                userService.createUser(sampleUser));
        assertEquals("O nome é obrigatório.", exception.getMessage());
    }

    @Test
    void createUser_ShouldThrowInvalidArgumentExceptionForInvalidEmail() {
        sampleUser.setEmail("invalid-email");
        InvalidArgumentException exception = assertThrows(InvalidArgumentException.class, () ->
                userService.createUser(sampleUser));
        assertEquals("E-mail inválido ou não informado.", exception.getMessage());
    }

    @Test
    void createUser_ShouldThrowInvalidArgumentExceptionForInvalidPassword() {
        sampleUser.setPassword("short");
        InvalidArgumentException exception = assertThrows(InvalidArgumentException.class, () ->
                userService.createUser(sampleUser));
        assertEquals("A senha deve ter pelo menos 8 caracteres, incluindo letra, número e caractere especial.",
                exception.getMessage());
    }

    @Test
    void createUser_ShouldThrowInvalidArgumentExceptionForNullBirthday() {
        sampleUser.setBirthday(null);
        InvalidArgumentException exception = assertThrows(InvalidArgumentException.class, () ->
                userService.createUser(sampleUser));
        assertEquals("Data de nascimento é obrigatória.", exception.getMessage());
    }

    @Test
    void shouldGetUserByIdSuccessfully() {
        when(userRepository.findByIdAndActiveTrue(sampleUser.getId())).thenReturn(Optional.of(sampleUser));

        Optional<User> result = userService.getUserById(sampleUser.getId());

        assertTrue(result.isPresent());
        assertEquals(sampleUser, result.get());
        verify(userRepository).findByIdAndActiveTrue(sampleUser.getId());
    }

    @Test
    void shouldReturnEmptyOptionalWhenGettingNonExistentUserById() {
        when(userRepository.findByIdAndActiveTrue("non-existent-id")).thenReturn(Optional.empty());

        Optional<User> result = userService.getUserById("non-existent-id");

        assertTrue(result.isEmpty());
        verify(userRepository).findByIdAndActiveTrue("non-existent-id");
    }

    @Test
    void shouldGetUserByEmailSuccessfully() {
        when(userRepository.findByEmailAndActiveTrue(sampleUser.getEmail())).thenReturn(Optional.of(sampleUser));

        Optional<User> result = userService.getUserByEmail(sampleUser.getEmail());

        assertTrue(result.isPresent());
        assertEquals(sampleUser, result.get());
        verify(userRepository).findByEmailAndActiveTrue(sampleUser.getEmail());
    }

    @Test
    void shouldReturnEmptyOptionalWhenGettingNonExistentUserByEmail() {
        when(userRepository.findByEmailAndActiveTrue("nonexistent@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userService.getUserByEmail("nonexistent@example.com");

        assertTrue(result.isEmpty());
        verify(userRepository).findByEmailAndActiveTrue("nonexistent@example.com");
    }

    @Test
    void shouldUpdateUserSuccessfully() {
        User existingUserForUpdate = new User();
        existingUserForUpdate.setId(sampleUser.getId());
        existingUserForUpdate.setUuid(sampleUser.getUuid());
        existingUserForUpdate.setName(sampleUser.getName());
        existingUserForUpdate.setEmail(sampleUser.getEmail());
        existingUserForUpdate.setPassword(encodedPassword); // Senha já codificada
        existingUserForUpdate.setBirthday(sampleUser.getBirthday());
        existingUserForUpdate.setCreatedAt(sampleUser.getCreatedAt());
        existingUserForUpdate.setUpdatedAt(sampleUser.getUpdatedAt());
        existingUserForUpdate.setActive(true);

        when(userRepository.findByIdAndActiveTrue(sampleUser.getId())).thenReturn(Optional.of(existingUserForUpdate));
        when(passwordEncoder.matches("NewPassword@123", encodedPassword)).thenReturn(false);
        when(passwordEncoder.encode("NewPassword@123")).thenReturn("$2a$10$newEncodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateUser(sampleUser.getId(), updatedUser);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();

        assertEquals(sampleUser.getId(), capturedUser.getId());
        assertEquals(updatedUser.getName(), capturedUser.getName());
        assertEquals(updatedUser.getEmail(), capturedUser.getEmail());
        assertEquals(updatedUser.getBirthday(), capturedUser.getBirthday());
        assertNotNull(capturedUser.getUpdatedAt());
        assertTrue(capturedUser.getUpdatedAt().isAfter(existingUserForUpdate.getUpdatedAt().minusSeconds(1)) ||
                capturedUser.getUpdatedAt().isEqual(existingUserForUpdate.getUpdatedAt()));
        assertTrue(capturedUser.isActive());

        // Verificar se a senha foi recodificada
        verify(passwordEncoder).matches("NewPassword@123", encodedPassword);
        verify(passwordEncoder).encode("NewPassword@123");

        assertEquals(capturedUser, result);
        verify(userRepository).findByIdAndActiveTrue(sampleUser.getId());
    }

    @Test
    void shouldNotReencodePasswordWhenSamePasswordOnUpdate() {
        User existingUserForUpdate = new User();
        existingUserForUpdate.setId(sampleUser.getId());
        existingUserForUpdate.setUuid(sampleUser.getUuid());
        existingUserForUpdate.setName(sampleUser.getName());
        existingUserForUpdate.setEmail(sampleUser.getEmail());
        existingUserForUpdate.setPassword(encodedPassword);
        existingUserForUpdate.setBirthday(sampleUser.getBirthday());
        existingUserForUpdate.setCreatedAt(sampleUser.getCreatedAt());
        existingUserForUpdate.setUpdatedAt(sampleUser.getUpdatedAt());
        existingUserForUpdate.setActive(true);

        // Usando a mesma senha
        updatedUser.setPassword(plainPassword);

        when(userRepository.findByIdAndActiveTrue(sampleUser.getId())).thenReturn(Optional.of(existingUserForUpdate));
        when(passwordEncoder.matches(plainPassword, encodedPassword)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.updateUser(sampleUser.getId(), updatedUser);

        // Verificar que a senha não foi recodificada
        verify(passwordEncoder).matches(plainPassword, encodedPassword);
        verify(passwordEncoder, never()).encode(plainPassword);
    }

    @Test
    void shouldThrowEntityNotFoundExceptionWhenUpdatingNonExistentUser() {
        when(userRepository.findByIdAndActiveTrue("non-existent-id")).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                userService.updateUser("non-existent-id", updatedUser));

        assertEquals("Usuário não encontrado para atualização ou inativo.", exception.getMessage());
        verify(userRepository).findByIdAndActiveTrue("non-existent-id");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldDeleteUserSuccessfully() {
        User existingUserForDelete = new User();
        existingUserForDelete.setId(sampleUser.getId());
        existingUserForDelete.setUuid(sampleUser.getUuid());
        existingUserForDelete.setName(sampleUser.getName());
        existingUserForDelete.setEmail(sampleUser.getEmail());
        existingUserForDelete.setPassword(sampleUser.getPassword());
        existingUserForDelete.setBirthday(sampleUser.getBirthday());
        existingUserForDelete.setCreatedAt(sampleUser.getCreatedAt());
        existingUserForDelete.setUpdatedAt(sampleUser.getUpdatedAt());
        existingUserForDelete.setActive(true);

        when(userRepository.findByIdAndActiveTrue(sampleUser.getId())).thenReturn(Optional.of(existingUserForDelete));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.deleteUser(sampleUser.getId());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();

        assertFalse(capturedUser.isActive());
        assertNotNull(capturedUser.getUpdatedAt());
        assertTrue(capturedUser.getUpdatedAt().isAfter(existingUserForDelete.getUpdatedAt().minusSeconds(1)) ||
                capturedUser.getUpdatedAt().isEqual(existingUserForDelete.getUpdatedAt()));

        verify(userRepository).findByIdAndActiveTrue(sampleUser.getId());
    }

    @Test
    void shouldThrowEntityNotFoundExceptionWhenDeletingNonExistentUser() {
        when(userRepository.findByIdAndActiveTrue("non-existent-id")).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                userService.deleteUser("non-existent-id"));

        assertEquals("Usuário não encontrado para exclusão ou já inativo.", exception.getMessage());
        verify(userRepository).findByIdAndActiveTrue("non-existent-id");
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldLoginSuccessfully() {
        when(userRepository.findByEmailAndActiveTrue(loginUser.getEmail())).thenReturn(Optional.of(loginUser));
        when(passwordEncoder.matches(plainPassword, encodedPassword)).thenReturn(true);

        Optional<User> result = userService.login(loginUser.getEmail(), plainPassword);

        assertTrue(result.isPresent());
        assertEquals(loginUser, result.get());
        verify(userRepository).findByEmailAndActiveTrue(loginUser.getEmail());
        verify(passwordEncoder).matches(plainPassword, encodedPassword);
    }

    @Test
    void shouldThrowInvalidCredentialsExceptionWhenPasswordDoesNotMatchOnLogin() {
        when(userRepository.findByEmailAndActiveTrue(loginUser.getEmail())).thenReturn(Optional.of(loginUser));
        when(passwordEncoder.matches("wrongPassword", encodedPassword)).thenReturn(false);

        InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class, () ->
                userService.login(loginUser.getEmail(), "wrongPassword"));

        assertEquals("Senha inválida.", exception.getMessage());
        verify(userRepository).findByEmailAndActiveTrue(loginUser.getEmail());
        verify(passwordEncoder).matches("wrongPassword", encodedPassword);
    }

    @Test
    void shouldReturnEmptyOptionalWhenUserNotFoundOnLogin() {
        when(userRepository.findByEmailAndActiveTrue("nonexistent@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userService.login("nonexistent@example.com", "anypassword");

        assertTrue(result.isEmpty());
        verify(userRepository).findByEmailAndActiveTrue("nonexistent@example.com");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}