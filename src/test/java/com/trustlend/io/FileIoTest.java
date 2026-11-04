package com.trustlend.io;

import com.trustlend.db.DatabaseManager;
import com.trustlend.engine.EngineSettings;
import com.trustlend.model.Vendor;
import com.trustlend.service.AppContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileIoTest {

    @Test
    @DisplayName("CSV values with commas and quotes are escaped")
    void csvEscaping() {
        assertEquals("plain", CsvExporter.escape("plain"));
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""));
    }

    @Test
    @DisplayName("Serialized backup restores the same data into a fresh database")
    void backupRoundTrip() throws Exception {
        Path dir = Files.createTempDirectory("trustlend-test");
        Path file = dir.resolve("backup.tlbak");

        try (DatabaseManager source = DatabaseManager.inMemory()) {
            source.initSchema();
            AppContext ctx = new AppContext(source, EngineSettings.defaults());
            ctx.startUp(true);   // seeds the demo community
            BackupSnapshot snap = ctx.backup().exportSnapshot(file);
            assertTrue(snap.getVendors().size() > 10);

            try (DatabaseManager target = DatabaseManager.inMemory()) {
                target.initSchema();
                AppContext restored = new AppContext(target, EngineSettings.defaults());
                restored.backup().importSnapshot(file);
                List<Vendor> vendors = restored.vendors().findAll();
                assertEquals(snap.getVendors().size(), vendors.size());
                assertEquals(snap.getTransactions().size(), restored.transactions().all().size());
            }
        }
    }

    @Test
    @DisplayName("CSV import skips bad lines and reports them")
    void csvImport() throws Exception {
        try (DatabaseManager db = DatabaseManager.inMemory()) {
            db.initSchema();
            AppContext ctx = new AppContext(db, EngineSettings.defaults());
            ctx.startUp(true);
            String phone = ctx.vendors().findAll().get(0).getPhone();
            Path csv = Files.createTempFile("txns", ".csv");
            Files.writeString(csv, "phone,date,type,amount,note\n"
                    + phone + ",2026-01-05,SALE,500,ok\n"
                    + "0000000000,2026-01-05,SALE,500,unknown vendor\n"
                    + phone + ",05/01/2026,SALE,500,bad date\n"
                    + phone + ",2026-01-06,REFUND,10,bad type\n", StandardCharsets.UTF_8);
            CsvTransactionImporter.Result r = ctx.importer().parse(csv);
            assertEquals(1, r.transactions().size());
            assertEquals(3, r.errors().size());
        }
    }
}
