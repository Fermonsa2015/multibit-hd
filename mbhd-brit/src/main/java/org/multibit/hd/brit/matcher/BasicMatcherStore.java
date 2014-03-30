package org.multibit.hd.brit.matcher;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.multibit.hd.brit.dto.BRITWalletId;
import org.multibit.hd.brit.dto.WalletToEncounterDateLink;
import org.multibit.hd.brit.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.Strings;

import java.io.*;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 *  <p>Store to provide the following to Matcher classes:<br>
 *  <ul>
 *  <li>File store and lookup of al bitcoin addresses. These are stored in the backingStoreDirectory/all.txt</li>
 * <li>File store and lookup of wallet to encounter date links. These are stored in a file backingStore/Directory/links.txt</li>
 * <li>File store and lookup of bitcoin addresses by day. For each date these are stored in a file backstoreDirectory/by-date/yyyy-mm-dd.txt</li>
 *  </ul>
 *  </p>
 *  
 */
public class BasicMatcherStore implements MatcherStore {

  private static final Logger log = LoggerFactory.getLogger(BasicMatcherStore.class);

  /**
   * The directory in which the backing files reside
   */
  private String backingStoreDirectory;

  public static final String NAME_OF_FILE_CONTAINING_ALL_BITCOIN_ADDRESSES = "all.txt";

  public static final String NAME_OF_FILE_CONTAINING_WALLET_TO_ENCOUNTER_DATE_LINKS = "links.txt";

  public static final String NAME_OF_DIRECTORY_CONTAINING_BITCOIN_ADDRESSES_BY_DATE = "by-date";

  public static final String LINKS_FILENAME_SUFFIX = ".txt";

  /**
   * Produces "2000-04-01" for simplified short user date
   */
  private static final DateTimeFormatter utcShortDateWithHyphensFormatter = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();

  /**
   * A map containing the link from a BRITWalletId to the previous encounter of this wallet (if available)
   */
  private Map<BRITWalletId, WalletToEncounterDateLink> previousEncounterMap;

  /**
   * The file to which the wallet to encounter dates are appended
   */
  private File walletToEncounterDateFile;

  /**
   * A list of all the Bitcoin addresses in the MatcherStore
   */
  private List<String> allBitcoinAddresses;

  /**
   * A map from the date of encounter to the list of Bitcoins used that day
   */
  private Map<Date, List<String>> encounterDateToBitcoinAddressesMap;

  public BasicMatcherStore(String backingStoreDirectory) {
    this.backingStoreDirectory = backingStoreDirectory;

    initialise(backingStoreDirectory);
  }

  /**
   * Initialise the MatchStore with the data stored at the backingStoreDirectory
   *
   * @param backingStoreDirectory The directory the matcher store backing files are stored in
   */
  private void initialise(String backingStoreDirectory) {
    // Load the file containing all the bitcoin addresses
    String allBitcoinAddressesFilename = backingStoreDirectory + File.separator + NAME_OF_FILE_CONTAINING_ALL_BITCOIN_ADDRESSES;
    allBitcoinAddresses = readBitcoinAddresses(allBitcoinAddressesFilename);

    encounterDateToBitcoinAddressesMap = Maps.newHashMap();
    // Go through all the files in the NAME_OF_DIRECTORY_CONTAINING_BITCOIN_ADDRESSES_BY_DATE directory
    // that have the filename yyyy-mm-dd.txt and add these bitcoin addresses as a list to the map, by the date yyyy-mm-dd
    String linksDirectory = backingStoreDirectory + File.separator + NAME_OF_DIRECTORY_CONTAINING_BITCOIN_ADDRESSES_BY_DATE;
    File[] linksFiles = (new File(linksDirectory)).listFiles();

    if (linksFiles != null) {
      for (File linkFile : linksFiles) {
        String filePart = FileUtils.filePart(linkFile.getAbsolutePath());
        // Remove any .txt
        filePart = filePart.replace(LINKS_FILENAME_SUFFIX, "");

        // See if it is a yyyy-mm-dd date
        DateTime parsedDate = utcShortDateWithHyphensFormatter.parseDateTime(filePart);
        if (parsedDate != null) {
          // This file contains the bitcoin addresses for this date
          List<String> bitcoinaddressesForDate = readBitcoinAddresses(linkFile.getAbsolutePath());
          encounterDateToBitcoinAddressesMap.put(parsedDate.toDate(), bitcoinaddressesForDate);
        }
      }
    }

    // Read in all the existing britWalletId to encounter date links
    previousEncounterMap = Maps.newHashMap();
    walletToEncounterDateFile = new File(backingStoreDirectory + File.separator + NAME_OF_FILE_CONTAINING_WALLET_TO_ENCOUNTER_DATE_LINKS);
    // If the file does not exists, then create it as we only ever append to this file later
    try {
      if (!walletToEncounterDateFile.exists()) {
        if (!walletToEncounterDateFile.createNewFile()) {
          log.debug("Could not create '" + walletToEncounterDateFile.getAbsolutePath() + "'");
        }
      }
      byte[] walletToEncounterDatesAsBytes = FileUtils.readFile(walletToEncounterDateFile); // Will scale better if streaming
      String walletToEncounterDates = new String(walletToEncounterDatesAsBytes, "UTF8");

      // Split into lines - each line contains a serialised WalletToEncounterDateLink
      String[] walletToEncounterDateArray = Strings.split(walletToEncounterDates, '\n');
      if (walletToEncounterDateArray != null) {
        for (String line : walletToEncounterDateArray) {
          if (line != null && !line.equals("")) {
            WalletToEncounterDateLink link = WalletToEncounterDateLink.parse(line);
            if (link != null) {
              previousEncounterMap.put(link.getBritWalletId(), link);
            }
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void storeWalletToEncounterDateLink(WalletToEncounterDateLink walletToEncounterDateLink) {
    // Update the in memory data representation
    previousEncounterMap.put(walletToEncounterDateLink.getBritWalletId(), walletToEncounterDateLink);

    // Append link data to backing file
    try {
      // true = append file
      FileWriter fileWriter = new FileWriter(walletToEncounterDateFile, true);
      BufferedWriter bufferWriter = new BufferedWriter(fileWriter);
      bufferWriter.write(walletToEncounterDateLink.serialise() + "\n");
      bufferWriter.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public WalletToEncounterDateLink lookupWalletToEncounterDateLink(BRITWalletId britWalletId) {
    // See if we have already seen this WalletId before

    // If this is present, return it.
    // If this is null, return a null.
    return previousEncounterMap.get(britWalletId);
  }

  @Override
  public List<String> getBitcoinAddressListForDate(Date encounterDate) {
    return encounterDateToBitcoinAddressesMap.get(convertToMidnight(encounterDate));
  }

  @Override
  public void storeBitcoinAddressListForDate(List<String> bitcoinAddressList, Date encounterDate) {
    // Update the in memory data representation
    encounterDateToBitcoinAddressesMap.put(convertToMidnight(encounterDate), bitcoinAddressList);

    // Also write to a file in the by-date directory
    String linksDirectory = backingStoreDirectory + File.separator + NAME_OF_DIRECTORY_CONTAINING_BITCOIN_ADDRESSES_BY_DATE;
    FileUtils.createDirectoryIfNecessary(new File(linksDirectory));

    String filename = linksDirectory + File.separator
            + utcShortDateWithHyphensFormatter.print(new DateTime(encounterDate, DateTimeZone.UTC)) + LINKS_FILENAME_SUFFIX;
    File file = new File(filename);

    if (file.exists()) {
      // Cannot overwrite a per day list of bitcoin addresses - it may have been sent back to Payers
      throw new IllegalArgumentException("Cannot write Bitcoin address list for date '" + encounterDate.toString() + "'. It already exists");
    }

    // Write the Bitcoin addresses to the file
    try {
      storeBitcoinAddressesToFile(bitcoinAddressList, filename);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void storeAllBitcoinAddresses(List<String> allBitcoinAddresses) {
    // Update the in memory data representation
    this.allBitcoinAddresses = allBitcoinAddresses;

    // Also write out to the all bitcoin addresses file
    String allBitcoinAddressesFilename = backingStoreDirectory + File.separator + NAME_OF_FILE_CONTAINING_ALL_BITCOIN_ADDRESSES;
    try {
      storeBitcoinAddressesToFile(allBitcoinAddresses, allBitcoinAddressesFilename);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public List<String> getAllBitcoinAddress() {
    return allBitcoinAddresses;
  }

  /**
   * Convert a compete date into a Date at midnight
   */
  private Date convertToMidnight(Date inputDate) {
    return (new DateTime(inputDate, DateTimeZone.UTC)).toDateMidnight().toDate();
  }

  private void storeBitcoinAddressesToFile(List<String> bitcoinAddresses, String filename) throws IOException {
    // Convert the bitcoin addresses to a byte array
    StringBuilder builder = new StringBuilder();
    if (bitcoinAddresses != null) {
      for (String address : bitcoinAddresses) {
        builder.append(address).append("\n");
      }
    }
    byte[] bitcoinAddressesAsBytes = builder.toString().getBytes("UTF8");

    FileOutputStream bitcoinAddressesFileOutputStream = new FileOutputStream(filename);
    FileUtils.writeFile(new ByteArrayInputStream(bitcoinAddressesAsBytes), bitcoinAddressesFileOutputStream);
  }

  private List<String> readBitcoinAddresses(String filename) {
    List<String> addresses = Lists.newArrayList();
    File addressesFile = new File(filename);
    if (addressesFile.exists()) {
      try {
        byte[] bitcoinAddressesAsBytes = FileUtils.readFile(addressesFile); // Will scale better if streaming
        String bitcoinAddresses = new String(bitcoinAddressesAsBytes, "UTF8");
        // Split into lines
        String[] bitcoinAddressLines = Strings.split(bitcoinAddresses, '\n');
        if (bitcoinAddressLines != null) {
          for (String line : bitcoinAddressLines) {
            if (line != null && !line.equals("")) {
              addresses.add(line);
            }
          }
        }
      } catch (IOException ioe) {
        log.error(ioe.getClass().getCanonicalName() + " " + ioe.getMessage());
      }
    }
    return addresses;
  }
}