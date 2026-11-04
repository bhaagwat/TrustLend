package com.trustlend.service;

import com.trustlend.dao.VendorDao;
import com.trustlend.dao.VouchDao;
import com.trustlend.exception.DataAccessException;
import com.trustlend.exception.EntityNotFoundException;
import com.trustlend.exception.InvalidVouchException;
import com.trustlend.exception.ValidationException;
import com.trustlend.model.Vendor;
import com.trustlend.model.Vouch;
import com.trustlend.util.Validators;

import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

/** Creates and removes vouches while enforcing anti-abuse rules. */
public class VouchService {

    private static final Logger LOG = Logger.getLogger(VouchService.class.getName());

    /** A vendor may vouch for at most this many others (limits vouch "farming"). */
    public static final int MAX_VOUCHES_GIVEN = 8;
    /** Vendors below this score cannot vouch for others. */
    public static final int MIN_VOUCHER_SCORE = 450;

    private final VouchDao vouchDao;
    private final VendorDao vendorDao;
    private final ScoreService scoreService;

    public VouchService(VouchDao vouchDao, VendorDao vendorDao, ScoreService scoreService) {
        this.vouchDao = vouchDao;
        this.vendorDao = vendorDao;
        this.scoreService = scoreService;
    }

    public Vouch addVouch(long voucherId, long voucheeId, int strength, Vouch.Relationship relationship)
            throws InvalidVouchException, ValidationException, DataAccessException, EntityNotFoundException {

        if (voucherId == voucheeId) {
            throw new InvalidVouchException("A vendor cannot vouch for themselves");
        }
        Validators.requireRange("strength", strength, Vouch.MIN_STRENGTH, Vouch.MAX_STRENGTH);
        if (relationship == null) {
            throw new ValidationException("relationship", "Choose how they know each other");
        }
        Vendor voucher = vendorDao.findById(voucherId).orElseThrow(() -> new EntityNotFoundException("Vendor", voucherId));
        Vendor vouchee = vendorDao.findById(voucheeId).orElseThrow(() -> new EntityNotFoundException("Vendor", voucheeId));
        if (!voucher.isActive() || !vouchee.isActive()) {
            throw new InvalidVouchException("Both vendors must be active");
        }
        if (vouchDao.find(voucherId, voucheeId).isPresent()) {
            throw new InvalidVouchException(voucher.getName() + " already vouches for " + vouchee.getName());
        }
        if (vouchDao.findGivenBy(voucherId).size() >= MAX_VOUCHES_GIVEN) {
            throw new InvalidVouchException(voucher.getName() + " has reached the limit of "
                    + MAX_VOUCHES_GIVEN + " vouches");
        }
        int voucherScore = scoreService.scoreValue(voucherId);
        if (voucherScore < MIN_VOUCHER_SCORE) {
            throw new InvalidVouchException(voucher.getName() + "'s score (" + voucherScore
                    + ") is below " + MIN_VOUCHER_SCORE + "; they cannot vouch yet");
        }
        Vouch v = vouchDao.save(new Vouch(0, voucherId, voucheeId, strength, relationship, LocalDate.now()));
        LOG.info(voucher.getName() + " vouched for " + vouchee.getName() + " (strength " + strength + ")");
        return v;
    }

    public boolean removeVouch(long vouchId) throws DataAccessException {
        return vouchDao.delete(vouchId);
    }

    public List<Vouch> given(long vendorId) throws DataAccessException {
        return vouchDao.findGivenBy(vendorId);
    }

    public List<Vouch> received(long vendorId) throws DataAccessException {
        return vouchDao.findReceivedBy(vendorId);
    }

    public List<Vouch> all() throws DataAccessException {
        return vouchDao.findAll();
    }
}
