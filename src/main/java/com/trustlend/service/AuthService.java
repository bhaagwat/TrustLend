package com.trustlend.service;

import com.trustlend.dao.UserDao;
import com.trustlend.exception.DataAccessException;
import com.trustlend.exception.ValidationException;
import com.trustlend.model.User;
import com.trustlend.util.PasswordHasher;

import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Accounts and log-in for borrowers, lenders and TrustLend HQ. Passwords are stored salted and
 * hashed, never in plain text. Sign-up forms that also create other records (a borrower's vendor
 * profile) live in {@link CommunityService} and use {@link #checkNewAccount} / {@link #create}.
 */
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_.]{3,20}$");

    private final UserDao userDao;
    private User currentUser;

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    /**
     * Validates username and password for a new account. Every failure names the form field.
     *
     * @return the normalised (lower-case) username
     */
    public String checkNewAccount(String username, String password, String confirm)
            throws ValidationException, DataAccessException {
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
        return user;
    }

    /** Saves a new account with a freshly salted password hash. */
    public User create(String username, String password, String fullName, User.Role role, long vendorId,
                       String organisation, String lenderType) throws DataAccessException {
        String salt = PasswordHasher.newSalt();
        User u = userDao.save(new User(0, normalise(username), PasswordHasher.hash(password, salt), salt, fullName,
                role, vendorId, organisation, lenderType));
        LOG.info("New " + role.getLabel().toLowerCase() + " account '" + u.getUsername() + "'");
        return u;
    }

    /**
     * Signs in. When {@code expected} is not null the account must have that role (the login page
     * asks "log in as Borrower / Lender / HQ"); HQ may also use the older OFFICER role.
     */
    public User login(String username, String password, User.Role expected) throws ValidationException, DataAccessException {
        User u = authenticate(username, password, expected);
        currentUser = u;
        LOG.info(u.getRole().getLabel() + " '" + u.getUsername() + "' logged in");
        return u;
    }

    /** Checks a username and password without changing who is signed in to the desktop app (used by the phone page). */
    public User authenticate(String username, String password, User.Role expected) throws ValidationException, DataAccessException {
        if (username == null || username.isBlank()) throw new ValidationException("username", "Enter your username");
        if (password == null || password.isEmpty()) throw new ValidationException("password", "Enter your password");
        Optional<User> user = userDao.findByUsername(normalise(username));
        if (user.isEmpty() || !PasswordHasher.matches(password, user.get().getSalt(), user.get().getPasswordHash())) {
            LOG.warning("Failed login for '" + username + "'");
            throw new ValidationException("password", "Incorrect username or password");
        }
        User u = user.get();
        if (expected != null && u.getRole() != expected && !(expected == User.Role.ADMIN && u.isAdmin())) {
            throw new ValidationException("username", "'" + u.getUsername() + "' is a " + u.getRole().getLabel().toLowerCase()
                    + " account — choose \"" + u.getRole().getLabel() + "\" above");
        }
        return u;
    }

    public User login(String username, String password) throws ValidationException, DataAccessException {
        return login(username, password, null);
    }

    boolean exists(String username) throws DataAccessException {
        return userDao.findByUsername(normalise(username)).isPresent();
    }

    public void setCurrentUser(User user) {
        currentUser = user;
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
