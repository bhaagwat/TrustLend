package com.trustlend.service;

import com.trustlend.dao.UserDao;
import com.trustlend.exception.DataAccessException;
import com.trustlend.exception.ValidationException;
import com.trustlend.model.User;
import com.trustlend.util.PasswordHasher;

import java.util.Optional;
import java.util.logging.Logger;

public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());

    public static final String DEFAULT_ADMIN = "admin";
    public static final String DEFAULT_PASSWORD = "admin123";

    private final UserDao userDao;
    private User currentUser;

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    /** Creates admin/admin123 on a fresh database. */
    public void ensureDefaultAdmin() throws DataAccessException {
        if (userDao.count() == 0) {
            String salt = PasswordHasher.newSalt();
            userDao.save(new User(0, DEFAULT_ADMIN, PasswordHasher.hash(DEFAULT_PASSWORD, salt), salt,
                    "Loan Officer", User.Role.ADMIN));
            LOG.info("Default admin account created");
        }
    }

    public User login(String username, String password) throws ValidationException, DataAccessException {
        if (username == null || username.isBlank()) throw new ValidationException("username", "Enter your username");
        if (password == null || password.isEmpty()) throw new ValidationException("password", "Enter your password");
        Optional<User> user = userDao.findByUsername(username.trim());
        if (user.isEmpty() || !PasswordHasher.matches(password, user.get().getSalt(), user.get().getPasswordHash())) {
            LOG.warning("Failed login for '" + username + "'");
            throw new ValidationException("password", "Incorrect username or password");
        }
        currentUser = user.get();
        LOG.info("User '" + username + "' logged in");
        return currentUser;
    }

    public Optional<User> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    public void logout() {
        currentUser = null;
    }
}
