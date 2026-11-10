package com.trustlend.service;

import com.trustlend.dao.UserDao;
import com.trustlend.exception.DataAccessException;
import com.trustlend.exception.ValidationException;
import com.trustlend.model.User;
import com.trustlend.util.PasswordHasher;
import com.trustlend.util.Validators;

import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Sign-up and sign-in against the shared accounts database. Passwords are stored salted and hashed. */
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_.]{3,20}$");

    private final UserDao userDao;
    private User currentUser;

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    /** Creates a new loan-officer account. Every failure names the form field to highlight. */
    public User signUp(String fullName, String username, String password, String confirm)
            throws ValidationException, DataAccessException {
        String name = Validators.requireName("name", fullName);
        String user = normalise(username);
        if (!USERNAME.matcher(user).matches()) {
            throw new ValidationException("username", "Username: 3-20 letters, digits, dot or underscore");
        }
        if (password == null || password.length() < 6) {
            throw new ValidationException("password", "Password must be at least 6 characters");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new ValidationException("password", "Password needs at least one letter and one number");
        }
        if (!password.equals(confirm)) {
            throw new ValidationException("confirm", "Passwords do not match");
        }
        if (userDao.findByUsername(user).isPresent()) {
            throw new ValidationException("username", "Username '" + user + "' is taken - try another");
        }
        String salt = PasswordHasher.newSalt();
        User created = userDao.save(new User(0, user, PasswordHasher.hash(password, salt), salt, name, User.Role.OFFICER));
        LOG.info("New account '" + user + "' created");
        currentUser = created;
        return created;
    }

    /** Creates an account without the interactive checks (used to adopt an old single-user database). */
    User createAccount(String username, String password, String fullName, User.Role role) throws DataAccessException {
        String salt = PasswordHasher.newSalt();
        return userDao.save(new User(0, username, PasswordHasher.hash(password, salt), salt, fullName, role));
    }

    public User login(String username, String password) throws ValidationException, DataAccessException {
        if (username == null || username.isBlank()) throw new ValidationException("username", "Enter your username");
        if (password == null || password.isEmpty()) throw new ValidationException("password", "Enter your password");
        Optional<User> user = userDao.findByUsername(normalise(username));
        if (user.isEmpty() || !PasswordHasher.matches(password, user.get().getSalt(), user.get().getPasswordHash())) {
            LOG.warning("Failed login for '" + username + "'");
            throw new ValidationException("password", "Incorrect username or password");
        }
        currentUser = user.get();
        LOG.info("User '" + currentUser.getUsername() + "' logged in");
        return currentUser;
    }

    boolean findAccount(String username) throws DataAccessException {
        return userDao.findByUsername(normalise(username)).isPresent();
    }

    public Optional<User> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    public void logout() {
        currentUser = null;
    }

    private static String normalise(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
