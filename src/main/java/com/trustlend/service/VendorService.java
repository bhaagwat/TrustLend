package com.trustlend.service;

import com.trustlend.dao.VendorDao;
import com.trustlend.exception.DataAccessException;
import com.trustlend.exception.EntityNotFoundException;
import com.trustlend.exception.ValidationException;
import com.trustlend.model.Vendor;
import com.trustlend.model.VendorCategory;
import com.trustlend.util.Validators;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/** Business rules for creating, editing and listing vendors. */
public class VendorService {

    private static final Logger LOG = Logger.getLogger(VendorService.class.getName());

    /** Reusable comparators (Unit 2). */
    public static final Comparator<Vendor> BY_NAME =
            Comparator.comparing(Vendor::getName, String.CASE_INSENSITIVE_ORDER);
    public static final Comparator<Vendor> BY_SCORE_DESC =
            Comparator.comparingInt(Vendor::getCurrentScore).reversed().thenComparing(BY_NAME);

    private final VendorDao vendorDao;
    private final ScoreService scoreService;

    public VendorService(VendorDao vendorDao, ScoreService scoreService) {
        this.vendorDao = vendorDao;
        this.scoreService = scoreService;
    }

    /** Validates the form values and inserts/updates the vendor. */
    public Vendor save(Long id, String name, String phone, VendorCategory category, String marketArea,
                       String city, int years, LocalDate joinDate)
            throws ValidationException, DataAccessException, EntityNotFoundException {

        String cleanName = Validators.requireName("name", name);
        String cleanPhone = Validators.requirePhone(phone);
        if (category == null) throw new ValidationException("category", "Choose a business category");
        String area = Validators.requireText("market area", marketArea);
        String cleanCity = Validators.requireText("city", city);
        Validators.requireRange("years in business", years, 0, 60);
        LocalDate joined = Validators.requireNotFuture("join date", joinDate);

        Optional<Vendor> samePhone = vendorDao.findByPhone(cleanPhone);
        if (samePhone.isPresent() && (id == null || samePhone.get().getId() != id)) {
            throw new ValidationException("phone", "Phone number already belongs to " + samePhone.get().getName());
        }

        Vendor vendor;
        if (id == null) {
            vendor = new Vendor(cleanName, cleanPhone, category, area, cleanCity, years, joined);
        } else {
            vendor = vendorDao.findById(id).orElseThrow(() -> new EntityNotFoundException("Vendor", id));
            vendor.setName(cleanName);
            vendor.setPhone(cleanPhone);
            vendor.setCategory(category);
            vendor.setMarketArea(area);
            vendor.setCity(cleanCity);
            vendor.setYearsInBusiness(years);
            vendor.setJoinDate(joined);
        }
        vendorDao.save(vendor);
        LOG.info((id == null ? "Created" : "Updated") + " vendor #" + vendor.getId() + " " + vendor.getName());
        return vendor;
    }

    public void setActive(long id, boolean active) throws DataAccessException, EntityNotFoundException {
        Vendor v = get(id);
        v.setActive(active);
        vendorDao.save(v);
    }

    public boolean delete(long id) throws DataAccessException {
        boolean deleted = vendorDao.delete(id);
        if (deleted) LOG.warning("Deleted vendor #" + id + " and all related records");
        return deleted;
    }

    public Vendor get(long id) throws DataAccessException, EntityNotFoundException {
        Vendor v = vendorDao.findById(id).orElseThrow(() -> new EntityNotFoundException("Vendor", id));
        scoreService.decorate(List.of(v));
        return v;
    }

    public List<Vendor> findAll() throws DataAccessException {
        return scoreService.decorate(vendorDao.findAll());
    }

    public List<Vendor> search(String text) throws DataAccessException {
        List<Vendor> list = (text == null || text.isBlank()) ? vendorDao.findAll() : vendorDao.search(text);
        return scoreService.decorate(list);
    }

    /** Vendors sorted by score, highest first. */
    public List<Vendor> ranked() throws DataAccessException {
        List<Vendor> list = findAll();
        list.sort(BY_SCORE_DESC);
        return list;
    }
}
