package com.falcon.auction.storage;

import java.io.*;
import java.nio.file.*;
import org.bukkit.configuration.file.FileConfiguration;

public final class AtomicFiles {
  public static void save(FileConfiguration cfg, File file) throws IOException {
    file.getParentFile().mkdirs();
    Path temp = Files.createTempFile(file.getParentFile().toPath(), file.getName(), ".tmp");
    try {
      cfg.save(temp.toFile());
      try {
        Files.move(
            temp,
            file.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }
}
