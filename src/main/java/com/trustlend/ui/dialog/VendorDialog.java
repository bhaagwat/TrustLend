package com.trustlend.ui.dialog;

import com.trustlend.exception.ValidationException;
import com.trustlend.model.Vendor;
import com.trustlend.model.VendorCategory;
import com.trustlend.service.VendorService;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.Collection;
import java.util.TreeSet;

/** Add / edit vendor form. */
public class VendorDialog extends FormDialog<Vendor> {

    private final VendorService service;
    private final Vendor existing;

    private final TextField name = new TextField();
    private final TextField phone = new TextField();
    private final ComboBox<VendorCategory> category = new ComboBox<>(FXCollections.observableArrayList(VendorCategory.values()));
    private final ComboBox<String> market = new ComboBox<>();
    private final TextField city = new TextField("New Delhi");
    private final Spinner<Integer> years = new Spinner<>(0, 60, 2);
    private final DatePicker joined = new DatePicker(LocalDate.now());

    public VendorDialog(VendorService service, Vendor existing, Collection<String> knownMarkets) {
        super(existing == null ? "Add vendor" : "Edit vendor",
                existing == null ? "Register a new vendor" : "Edit " + existing.getName(),
                existing == null ? "Add vendor" : "Save changes");
        this.service = service;
        this.existing = existing;

        name.setPromptText("e.g. Sunita Devi");
        phone.setPromptText("10-digit mobile number");
        // Only allow digits, spaces, + and - while typing (TextFormatter filter)
        phone.setTextFormatter(new javafx.scene.control.TextFormatter<String>(change ->
                change.getControlNewText().matches("[0-9+\\- ]{0,14}") ? change : null));
        category.setPromptText("Choose category");
        market.setItems(FXCollections.observableArrayList(new TreeSet<>(knownMarkets)));
        market.setEditable(true);
        market.setPromptText("Market / bazaar name");
        years.setEditable(true);

        addRow("Full name", "name", name);
        addRow("Mobile", "phone", phone);
        addRow("Business", "category", category);
        addRow("Market area", "market area", market);
        addRow("City", "city", city);
        addRow("Years trading", "years in business", years);
        addRow("Joined TrustLend", "join date", joined);

        if (existing != null) {
            name.setText(existing.getName());
            phone.setText(existing.getPhone());
            category.setValue(existing.getCategory());
            market.setValue(existing.getMarketArea());
            city.setText(existing.getCity());
            years.getValueFactory().setValue(existing.getYearsInBusiness());
            joined.setValue(existing.getJoinDate());
        }
        javafx.application.Platform.runLater(name::requestFocus);
    }

    @Override
    protected Vendor submit() throws Exception {
        Integer y;
        try {
            y = Integer.parseInt(years.getEditor().getText().trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("years in business", "Years trading must be a whole number");
        }
        String marketText = market.getEditor().getText();
        return service.save(existing == null ? null : existing.getId(), name.getText(), phone.getText(),
                category.getValue(), marketText, city.getText(), y, joined.getValue());
    }
}
