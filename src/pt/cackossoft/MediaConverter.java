package pt.cackossoft;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.time.DateUtils;
import javax.swing.*;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by cecoutinho on 23-07-2014.
 */
public class MediaConverter {
    // Option for converters
    private final String[] fConverters = { "Check Converted Files"
                                         , "Rename: Add prefix Timestamp + Camera Model"
                                         , "Rename: Remove Regex & Add prefix Timestamp + Camera Model"
                                         , "Rename: Replace Regex by User fixed text"
                                         , "Rename: Update File Time adding minutes"
                                         , "Wiko/Samsung Galaxy S2/S3/Tab/Ace" };

    private final String[] fMovieExtensions = { ".avi", ".mpg", ".mov", ".3gp" };
    private final String[] fPhotoExtensions = { ".jpg", ".png", ".gif" };
    private Metadata exifMetadata;          // Exif Metadata for the current photo (must be cleared every time the photo changes).
    private String exifCameraModel;         // Original Camera model name for photos (must be reset every time a new folder is selected).
    private String exifCameraModelUser;     // User-defined Camera model name for photos
    private BufferedReader mediaMetadata;         // Media Metadata for the current file (must be cleared every time the file changes).
    private String movieCameraModel;        // Original Camera model name for movies (must be reset every time a new folder is selected).
    private String movieCameraModelUser;    // User-defined Camera model name for movies
    private String replaceRegex;            // Regex to be replaced by new text or by Timestamp_CameraModel
    private String replacementUserText;     // New text to replace the Regex for
    private int minutesToBeAdded;           // Number of minutes to add the file timestamp
    private final String[] fKnownCameraModelIDs = { "Canon Canon DIGITAL IXUS 70", "CanonMVI06", "NIKON CORPORATION NIKON D3200", "WIKO                            HIGHWAY                        " };
    private final String[] fKnownCameraModelNames = { "canon_ixus70_101", "canon_ixus70_101", "nikon_d3200_100", "wiko_highway" };

    /**
     * Renames a file
     * @param aFile original file
     * @param aNewName new name of the file
     * @return success
     */
    private Boolean renameFile(File aFile, String aNewName) {
        String lNewName = aNewName;
        for (int lNum = 0;; ++lNum) {
            if (lNum > 0) {
                // This is to cover different versions of the same file, if the file already exists (appends a number)
                int lIndexExtension = aNewName.lastIndexOf(".");
                lNewName = aNewName.substring(0, lIndexExtension) + "-" + lNum + aNewName.substring(lIndexExtension);
            }
            if (!new File(lNewName).exists()) {
                break;
            }
        }
        return aFile.renameTo(new File(lNewName));
    }

    /**
     * Returns the proposed file new name, according to the selected converter.
     * @param f the file that is being analysed
     * @param aConverter the converter name (from the fConverters array)
     * @return "" if the file does not need renaming; the new name otherwise.
     */
    private String getNewFilePath(File f, String aConverter) {
        int lConverter = ArrayUtils.indexOf(fConverters, aConverter);
        switch(lConverter) {
            case 0: return verifyFile(f);
            case 1: return getNewFilePath_AddPrefixTimestampCamera(f);
            case 2: return getNewFilePath_RemoveRegexAddPrefixTimestampCamera(f);
            case 3: return getNewFilePath_ReplaceRegexByUserText(f);
            case 4: return getNewFilePath_UpdatePrefixTimeAddMinutes(f);
            case 5: return getNewFilePath_samsung(f);
        }
        return "";
    }

    /**
     * If a Camera Model ID matches any of the known IDs, return the corresponding Camera Known Name.
     * @param aCameraModel the ID returned by EXIF or other tool
     * @return the name that you wish this camera to be called (from fKnownCameraModelNames)
     */
    private String getKnownCameraModel(String aCameraModel) {
        for (int i = fKnownCameraModelIDs.length; i-- > 0;) {
            if (fKnownCameraModelIDs[i].equals(aCameraModel)) {
                return fKnownCameraModelNames[i];
            }
        }
        return aCameraModel;
    }

    /**
     * Runs the Media Converter/Verifier
     * @param args the default folder for the media
     */
    public void main(String[] args) {
        String lDefaultFolder = "";
        if (args.length > 0) {
            lDefaultFolder = args[0];
        }
        for (;;) {
            // Get Path of the folder to be converted
            String lSrcFolderPath = lDefaultFolder = getSrcFolderPath(lDefaultFolder);
            if (lSrcFolderPath.isEmpty()) {
                JOptionPane.showMessageDialog(null, "No Folder was selected. Exiting...", "Media Classifier", JOptionPane.INFORMATION_MESSAGE);
                break;
            }
            // Get Converter
            String lConverter = (String) JOptionPane.showInputDialog(null, "Apply file rename converter:", "Media Converter", JOptionPane.QUESTION_MESSAGE, null, fConverters, fConverters[0]);

            // Get Regex to be replaced/removed if option was selected
            replaceRegex = null;
            if (fConverters[2].equals(lConverter) || fConverters[3].equals(lConverter)) {
                replaceRegex = JOptionPane.showInputDialog(null, "Specify the Regex to be replaced:", "Media Converter", JOptionPane.QUESTION_MESSAGE);
                if (null == replaceRegex || replaceRegex.isEmpty()) {
                    continue;
                }
            }

            // Get the User text to replace, if option was selected
            replacementUserText = null;
            if (fConverters[3].equals(lConverter)) {
                replacementUserText = JOptionPane.showInputDialog(null, "Specify the text to be inserted:", "Media Converter", JOptionPane.QUESTION_MESSAGE);
                if (null == replacementUserText) {
                    continue;
                }
            }

            // Get the number of minutes to add, if option was selected
            String lMinutesToBeAdded = null;
            if (fConverters[4].equals(lConverter)) {
                lMinutesToBeAdded = JOptionPane.showInputDialog(null, "Specify the number of minutes to be added to each file timestamp:", "Media Converter", JOptionPane.QUESTION_MESSAGE);
                if (null == lMinutesToBeAdded) {
                    continue;
                }
                minutesToBeAdded = Integer.parseInt(lMinutesToBeAdded);
            }

            // Confirm Actions
            if (JOptionPane.OK_OPTION != JOptionPane.showConfirmDialog(null, "Please Confirm the parameters:\n\nSelected folder: " + lSrcFolderPath
                    + "\nSelected Action: " + lConverter + (null == replaceRegex ? "" : "\nRegex Text to be replaced: \"" + replaceRegex + "\"")
                    + (null == replacementUserText ? "" : "\nText to replace the above regex: \"" + replacementUserText + "\"")
                    + (null == lMinutesToBeAdded ? "" : "\nMinutes to be added to each timestamp: " + minutesToBeAdded),
                    "Input Confirmation", JOptionPane.OK_CANCEL_OPTION)) {
                JOptionPane.showMessageDialog(null, "Conversion Cancelled.", "Media Classifier", JOptionPane.INFORMATION_MESSAGE);
                continue;
            }

            // Cycle all files in the src folder
            movieCameraModel = exifCameraModel = "@@No Camera Model@@";    // Default value to be replaced on each run
            File[] listOfFiles = new File(lSrcFolderPath).listFiles();
            if (listOfFiles != null) {
                for (File listOfFile : listOfFiles) {
                    if (listOfFile.isFile()) {
                        File f = new File(listOfFile.getAbsolutePath());

                        // Process file
                        exifMetadata = null;
                        mediaMetadata = null;
                        String lNewName = getNewFilePath(f, lConverter);
                        // Only renames if the new name is valid
                        if (!lNewName.isEmpty()) {
                            System.out.println("Rename file: " + f.getAbsolutePath() + " to " + lNewName + (renameFile(f, lNewName) ? ": Renamed successfully" : ": Error in rename"));
                        }
                    }
                }
            }
            JOptionPane.showMessageDialog(null, "Conversion finished.", "Media Classifier", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Opens a folder selection dialog and returns the selected path.
     * @param aDefaultFolder the default folder for the media
     * @return "" if the user did not select any folder (Cancelled), or the folder path otherwise.
     */
    private String getSrcFolderPath(String aDefaultFolder) {
        JFileChooser lFileDialog = new JFileChooser(aDefaultFolder);
        lFileDialog.setDialogTitle("Select folder to be converted");
        lFileDialog.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        lFileDialog.setAcceptAllFileFilterUsed(false);
        return lFileDialog.showOpenDialog(null) == JFileChooser.APPROVE_OPTION ? lFileDialog.getSelectedFile().getAbsolutePath() : "";
    }

    /**
     * Media metadata reading methods.
     */

    /**
     * Populates the mediaMetadata property with data for the MEDIA analysis.
     * @param f the file that is being analysed
     * @param aKey the key for which we want to get the value
     * @return Value for the passed key, if any. Otherwise, null
     */
    private String getMediaMetadataValue(File f, String aKey) {
        if (null == mediaMetadata) {
            // Invoke external program "mediainfo"
            ProcessBuilder pb = new ProcessBuilder("mediainfo", "-f", f.getAbsolutePath());
            Process p = null;
            try {
                p = pb.start(); // Execute the program
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (p != null) {
                try {
                    p.waitFor(); // Wait for the end of the program
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // Read the program's stdout (here it is the input stream)
                mediaMetadata = new BufferedReader(new InputStreamReader(p.getInputStream()));
                try {
                    mediaMetadata.mark(4096);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        String lStrKeyValue = null;
        try {
            mediaMetadata.reset();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        do {
            try {
                lStrKeyValue = mediaMetadata.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Stop at the required key
            if (lStrKeyValue != null && lStrKeyValue.startsWith(aKey)) {
               break;
            }
        } while (lStrKeyValue != null);
        return null == lStrKeyValue ? null : lStrKeyValue.split(": ")[1].trim();
    }

    /**
     * Returns the timestamp of the movie
     * @param f the file that is being analysed
     * @return the proposed file timestamp or null in case of error
     */
    private Date getMediaTimeStamp(File f) {
        // Cycle through all possible keys, break if any of them work
        String lTimestamp = null;
        final String[] possibleKeys = { "Mastered date", "Encoded date" };
        for (String lKey : possibleKeys) {
            if (null != (lTimestamp = getMediaMetadataValue(f, lKey))) {
                break;
            }
        }

        // Cycle through all possible timedate encodings, return if any of them work
        final String[] possibleEncodings = { "EEE MMM dd HH:mm:ss yyyy", "yyyy-MM-dd HH:mm:ss", "ZZZ yyyy-MM-dd HH:mm:ss" };
        for (String lEncoding : possibleEncodings) {
            try {
                return new SimpleDateFormat(lEncoding).parse(lTimestamp);
            } catch (ParseException e) {
                // do nothing;
            }
        }
        return null;
    }

    /**
     * Asks the user to confirm the camera Model that was selected
     * @param aFileName Name of the media file
     * @param aCameraModel Name of the extracted Camera Model
     * @return the name that the user wants to give to that Camera Model
     */
    private String getUserConfirmationCameraModel(String aFileName, String aCameraModel) {
        int lConfirmResponse;
        String lCameraModelUser;
        do {
            lCameraModelUser = JOptionPane.showInputDialog("Camera Model for file " + aFileName + " (and similar):", aCameraModel.trim()).trim().toLowerCase().replaceAll("[^\\w]", "_");
            // Ask user Confirmation
            lConfirmResponse = JOptionPane.showConfirmDialog(null, "Camera Model information for file " + aFileName +
                    " and similar will be \n\t\t\"" + lCameraModelUser + "\"\nConfirm?", "Input Confirmation", JOptionPane.YES_NO_CANCEL_OPTION);
            if (JOptionPane.CANCEL_OPTION == lConfirmResponse) {
                JOptionPane.showMessageDialog(null, "Conversion Cancelled.", "Media Classifier", JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
            }
        } while (JOptionPane.YES_OPTION != lConfirmResponse);
        return lCameraModelUser;
    }

    /**
     * Returns the camera model of the movie
     * @param f the file that is being analysed
     * @return the file camera model name
     */
    private String getMediaCameraModel(File f) {
        // Cycle through all possible keys, break if any of them work
        String lCameraModel = null;
        final String[] possibleKeys = { "Writing application", "Title" };
        for (String lKey : possibleKeys) {
            if (null != (lCameraModel = getMediaMetadataValue(f, lKey))) {
                break;
            }
        }
        // Internal conversion of frequent known formats
        lCameraModel = getKnownCameraModel(lCameraModel);
        // Get User confirmation
        if (!movieCameraModel.equals(lCameraModel)) {    // Compare the current Camera Model with the previous file's (default is @@No Camera Model@@)
            movieCameraModel = lCameraModel;             // If it is different, ask the user which will be the name the user wants to give to the new Camera Model
            movieCameraModelUser = getUserConfirmationCameraModel(f.getName(), lCameraModel);
        }
        return movieCameraModelUser;
    }

    /**
     * EXIF metadata reading methods.
     */

    /**
     * Populates the exifMetadata property with data for the EXIF analysis.
     * @param f the file that is being analysed
     * @return success. if True, then exifMetadata is populated
     */
    private boolean getExifMetadataValue(File f) {
        if (null == exifMetadata) {
            try {
                exifMetadata = ImageMetadataReader.readMetadata(f);
            } catch (ImageProcessingException e) {
                e.printStackTrace();
                return false;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the EXIF timestamp of the photo
     * @param f the file that is being analysed
     * @return the proposed file timestamp or null in case of error
     */
    private Date getExifTimestamp(File f) {
        if (!getExifMetadataValue(f)) return null;      // In case of any EXIF error, return null

        // Get Filestamp from EXIF
        ExifSubIFDDirectory directory = exifMetadata.getDirectory(ExifSubIFDDirectory.class);
        try {
            return directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
        } catch (NullPointerException e) {  // This exception occurs when the photo was modified, hence there is EXIF but no EXIF timestamp
            e.printStackTrace();
            return null;      // In case of any EXIF error, return null
        }
    }

    /**
     * Returns the EXIF camera model of the photo
     * @param f the file that is being analysed
     * @return the file camera model name
     */
    private String getExifCameraModel(File f) {
        if (!getExifMetadataValue(f)) return "";      // In case of any EXIF error, return ""

        // Get Camera Model from EXIF
        ExifIFD0Directory directory = exifMetadata.getDirectory(ExifIFD0Directory.class);
        String lCameraModel;
        try {
            lCameraModel = directory.getDescription(ExifIFD0Directory.TAG_MAKE) + " " + directory.getDescription(ExifIFD0Directory.TAG_MODEL);
        } catch (NullPointerException e) {  // This exception occurs when the photo was modified, hence there is EXIF but no EXIF timestamp
            e.printStackTrace();
            return "";      // In case of any EXIF error, return ""
        }
        // Internal conversion of frequent known formats
        lCameraModel = getKnownCameraModel(lCameraModel);
        // Get User confirmation
        if (!exifCameraModel.equals(lCameraModel)) {    // Compare the current Camera Model with the previous file's (default is @@No Camera Model@@)
            exifCameraModel = lCameraModel;             // If it is different, ask the user which will be the name the user wants to give to the new Camera Model
            exifCameraModelUser = getUserConfirmationCameraModel(f.getName(), lCameraModel);
        }
        return exifCameraModelUser;
    }

    /**
     * Returns the file extension
     * @param f the file that is being analysed
     * @return the file extension (with "." included) in lowercase
     */
    private String getFileExtension(File f) {
        return f.getName().substring(f.getName().lastIndexOf(".")).toLowerCase();
    }

    /**
     * By default gets the last modified timestamp. If the file has metadata information, returns instead the metadata timestamp of the photo
     * @param f the file that is being analysed
     * @return the proposed file timestamp
     */
    private String getFileTimestamp(File f) {
        Date lTimeStamp;
        String lFileExtension = getFileExtension(f);
        lTimeStamp = ArrayUtils.contains(fPhotoExtensions, lFileExtension) ? getExifTimestamp(f) : getMediaTimeStamp(f);
        if (null == lTimeStamp) {
            // Get Filestamp from File Last Modified
            lTimeStamp = new Date(f.lastModified());
        }

            // @Temporary Fix: If there is need to change the timestamp (due to change of timezone or camera time not correct), change here with an offset
            // lTimeStamp = DateUtils.addMinutes(lTimeStamp, -67);

        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(lTimeStamp);
    }

    /**
     * Tries to get the camera model name, and adapts it if needed
     * @param f the file that is being analysed
     * @return the camera model name
     */
    private String getFileCameraModel(File f) {
        return ArrayUtils.contains(fPhotoExtensions, getFileExtension(f)) ? getExifCameraModel(f) : getMediaCameraModel(f);
    }

    /**
     * Checks whether the file can be renamed (if file has not yet been processed)
     * @param f the file that is being analysed
     * @return true if file needs to be processed
     */
    private boolean isFileAlreadyHandled(File f) {
            // @Temporary Fix: To avoid multiple interactions with the user when there are multiple files like "*.thm", use this, and then run again removing the comments
            //   if (f.getName().toLowerCase().endsWith(".thm")) {
            //      return true;
            //   }

        // Avoid processing already processed files: Accept all names that do not start by "my" timestamp
        return f.getName().matches("^(19|20)\\d\\d(0[1-9]|1[012])(0[1-9]|[12][0-9]|3[01])_([01][0-9]|2[0-3])[0-5][0-9][0-5][0-9]-.*");
    }

    /**
     * Gets a new prefix for the file, which starts with a file timestamp, followed by the camera model.
     * @param f the file that is being analysed
     * @return "" if a problem occurred (e.g., the file already has the new format); the new prefix name otherwise.
     */
    private String getFilePrefix_TimestampCamera(File f) {
        String lFilePrefix = getFileTimestamp(f);
        if (lFilePrefix.isEmpty()) return "";
        String lCameraModel = getFileCameraModel(f);
        return lFilePrefix + (lCameraModel.isEmpty() ? "" : "-" + lCameraModel);
    }

    /**
     * Gets the new Filename path for media taken on: All Samsung Phones except Ace (change the lStaticInfo)
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     */
    private String getNewFilePath_samsung(File f) {
        if (isFileAlreadyHandled(f)) return "";
        // If the file has any prefix, store it
        String lPrefix = "-" + f.getName().split("_")[0];
        if (lPrefix.matches("^-(19|20)\\d\\d(0[1-9]|1[012])(0[1-9]|[12][0-9]|3[01])")) {
            lPrefix = "";
        }
        String lTimestamp = f.getName().substring(lPrefix.length(), lPrefix.length() + 15);   // It is better to use the timestamp on the filename than to get it from Exif
        String lNewFilePath = lTimestamp + "-" + getFileCameraModel(f) + lPrefix.toLowerCase() + getFileExtension(f);

        // Add folder (in this case it is the same)
        lNewFilePath = f.getParent() + File.separator + lNewFilePath;
        return lNewFilePath;
    }

    /**
     * Gets the new Filename path for media taken on: Samsung Galaxy Ace
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     *
    private String getNewFilePath_samsungAce(File f) {
        // Avoid processing already processed files: Format of the filenames: "yyyy-MM-dd HH.mm.ss".jpg or video-"yyyy-MM-dd-HH-mm-ss".mp4
        if (f.getName().length() > 30) { return ""; }
        String lStaticInfo = "-samsung_ace";
        String lFilename = f.getName();
        if (lFilename.substring(0, 6).equals("video-")) {
            lFilename = lFilename.substring(6);
        }
        String lTimestamp = lFilename.substring(0, 4) + lFilename.substring(5, 7) + lFilename.substring(8, 10) + "_" + lFilename.substring(11, 13) + lFilename.substring(14, 16) + lFilename.substring(17, 19);
        String lNewFilePath = lTimestamp + lStaticInfo + f.getName().substring(f.getName().length()-4).toLowerCase();

        // Add folder (in this case it is the same)
        lNewFilePath = f.getParent() + File.separator + lNewFilePath;
        return lNewFilePath;
    }

    /**
     * Verifies if the file has the correct name according to its timestamp (coming from EXIF or other).
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     */
    private String verifyFile(File f) {
        String lFilenameTimeStamp = f.getName().substring(0, 15);
        final String lStaticInfo = f.getName().substring(15);
        String lNewFilePath = getFileTimestamp(f) + lStaticInfo;
        if (!lFilenameTimeStamp.substring(0, 9).equals(lNewFilePath.substring(0, 9))) {
            int lConfirmationResult = JOptionPane.showConfirmDialog(null, "We found a discrepancy in the following file:\n\nThe media file:  " + f.getName() + "\nshould be named: " + lNewFilePath + "\n\n", "Input Confirmation", JOptionPane.YES_NO_CANCEL_OPTION);
            if (JOptionPane.CANCEL_OPTION == lConfirmationResult) {
                System.out.println("Verification Cancelled.");
                System.exit(0);
            }
            else if (JOptionPane.YES_OPTION == lConfirmationResult) {
                // Add folder (in this case it is the same)
                lNewFilePath = f.getParent() + File.separator + lNewFilePath;
                return lNewFilePath;
            }
        }
        return "";
    }

    /**
     * Gets the new Filename path for media taken on any source, prefixing it with Timestamp and Camera Model
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     */
    private String getNewFilePath_AddPrefixTimestampCamera(File f) {
        if (isFileAlreadyHandled(f)) return ""; // This check is to avoid reprocessing files already handled
        String lNewFilePath = getFilePrefix_TimestampCamera(f);
        if (lNewFilePath.isEmpty()) return "";
        String lOldName = f.getName().toLowerCase().replaceAll("[^\\w.]", "_");

        /*  // @TODO Uncomment this code to change the photo numbering
            int lIdxNumberStt = lOldName.indexOf("_") + 1;  // File names are typically "text_123[_1].extension"
            if (lIdxNumberStt > 0) {                        // Search for the first occurrence of "_" in the name
                int lIdxNumberEnd = lOldName.indexOf("_", lIdxNumberStt);   // Now search for any next occurrence of "_"
                if (lIdxNumberEnd < 0) {                    // Maybe this file has no more "_"s
                    lIdxNumberEnd = lOldName.indexOf(".", lIdxNumberStt);   // But it must then have a ".extension"
                }
                if (lIdxNumberEnd > 0) {
                    String lOldNumber = lOldName.substring(lIdxNumberStt, lIdxNumberEnd);
                    int lNewNumber = Integer.parseInt(lOldNumber);
                    lNewNumber += 1029;     // @IMPORTANT this is the Offset that needs to be added to the photo number
                    lOldName = lOldName.substring(0, lIdxNumberStt) + lNewNumber + lOldName.substring(lIdxNumberEnd);
                }
            }
        */

        lNewFilePath += "-" + lOldName;

        // Add folder (in this case it is the same)
        lNewFilePath = f.getParent() + File.separator + lNewFilePath;
        return lNewFilePath;
    }

    /**
     * Gets the new Filename path for media, removing the input Regex and prefixing it with Timestamp and Camera Model
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     * Example: To change the photo time, call this with Regex: "^(19|20)\d\d(0[1-9]|1[012])(0[1-9]|[12][0-9]|3[01])_([01][0-9]|2[0-3])[0-5][0-9][0-5][0-9]-canon_ixus70_101-"
     */
    private String getNewFilePath_RemoveRegexAddPrefixTimestampCamera(File f) {
            // @Temporary Fix: To avoid multiple interactions with the user when there are multiple files like "*.thm", use this, and then run again removing the comments
            //   if (f.getName().toLowerCase().endsWith(".thm")) {
            //      return "";
            //   }

        String lOldFilename = f.getName().toLowerCase().replaceAll(replaceRegex, "");
        if (f.getName().toLowerCase().equals(lOldFilename)) return "";    // If Regex not found, do not rename file
        String lNewFilePath = getFilePrefix_TimestampCamera(f);
        if (lNewFilePath.isEmpty()) return "";
        lNewFilePath += (lOldFilename.startsWith(".") || lOldFilename.startsWith("-") || lOldFilename.startsWith("_") ? "" : "-") + lOldFilename.replaceAll("[^\\w.]", "_");

        // Add folder (in this case it is the same)
        lNewFilePath = f.getParent() + File.separator + lNewFilePath;
        return lNewFilePath;
    }

    /**
     * Gets the new Filename path for media, replacing the input Regex by a user fixed text
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     */
    private String getNewFilePath_ReplaceRegexByUserText(File f) {
        String lNewFilePath = f.getName().toLowerCase().replaceAll(replaceRegex, replacementUserText).replaceAll("[^\\w._-]", "_");
        if (f.getName().toLowerCase().equals(lNewFilePath)) return "";    // If Regex not found, do not rename file

        // Add folder (in this case it is the same)
        lNewFilePath = f.getParent() + File.separator + lNewFilePath;
        return lNewFilePath;
    }

    /**
     * Gets the new Filename path for media, updating the existing file timestamp by adding minutes to that timestamp
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     */
    private String getNewFilePath_UpdatePrefixTimeAddMinutes(File f) {
        String lFilenameTimeStamp = f.getName().substring(0, 15);
        Date lFilenameTimestamp;
        try {
            lFilenameTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").parse(lFilenameTimeStamp);
        } catch (ParseException e) {
            return "";
        }
        lFilenameTimestamp = DateUtils.addMinutes(lFilenameTimestamp, minutesToBeAdded);
        String lNewFilePath = new SimpleDateFormat("yyyyMMdd_HHmmss").format(lFilenameTimestamp) + f.getName().toLowerCase().substring(15).replaceAll("[^\\w._-]", "_");

        // Add folder (in this case it is the same)
        lNewFilePath = f.getParent() + File.separator + lNewFilePath;
        return lNewFilePath;
    }
}