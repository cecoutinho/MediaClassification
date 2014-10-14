package pt.cackossoft;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.apache.commons.lang.ArrayUtils;

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
                                         , "Samsung Galaxy S2/S3/Tab"
                                         , "Samsung Galaxy Ace" };

    // private final String[] fMovieExtensions = { ".avi", ".mpg", ".mov" };
    private final String[] fPhotoExtensions = { ".jpg", ".png", ".gif" };
    private Metadata exifMetadata;          // Exif Metadata for the current photo (must be cleared every time the photo changes).
    private String exifCameraModel;         // Original Camera model name for photos (must be reset every time a new folder is selected).
    private String exifCameraModelUser;     // User-defined Camera model name for photos
    private String movieCameraModel;        // Original Camera model name for movies (must be reset every time a new folder is selected).
    private String movieCameraModelUser;    // User-defined Camera model name for movies
    private String replaceRegex;            // Regex to be replaced by new text or by Timestamp_CameraModel
    private String replacementUserText;         // New text to replace the Regex for

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
            case 4: return getNewFilePath_samsung(f);
            case 5: return getNewFilePath_samsung(f);
        }
        return "";
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

            // Confirm Actions
            if (JOptionPane.OK_OPTION != JOptionPane.showConfirmDialog(null, "Please Confirm the parameters:\n\nSelected folder: " + lSrcFolderPath
                    + "\nSelected Action: " + lConverter + (null == replaceRegex ? "" : "\nRegex Text to be replaced: \"" + replaceRegex + "\"")
                    + (null == replacementUserText ? "" : "\nText to replace the above regex: \"" + replacementUserText + "\""),
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
                int lIndexExtension = aNewName.lastIndexOf(File.separator);
                lNewName = aNewName.substring(0, lIndexExtension) + "-" + lNum + aNewName.substring(lIndexExtension);
            }
            if (!new File(lNewName).exists()) {
                break;
            }
        }
        return aFile.renameTo(new File(lNewName));
    }

    /**
     * Movie metadata reading methods.
     */

    /**
     * Returns the specified metadata of the movie
     * @param f the file that is being analysed
     * @return the specified file metadata if it exists, or "" in case of error or not found
     */
    private String getMovieMetadata(File f, String aKey) {
        // Invoke external program "mediainfo"
        ProcessBuilder pb = new ProcessBuilder("mediainfo", f.getAbsolutePath());
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
        }
        if (p != null) {
            // Read the program's stdout (here it is the input stream)
            BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String input = null;
            do {
                try {
                    input = bri.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Stop at the required key
                if (input != null && input.startsWith(aKey)) {
                   break;
                }
            } while (input != null);
            return null == input ? "" : input.split(": ")[1].trim();
        }
        return "";
    }

    /**
     * Returns the timestamp of the movie
     * @param f the file that is being analysed
     * @return the proposed file timestamp or null in case of error
     */
    private Date getMovieTimeStamp(File f) {
        // Cycle through all possible keys, break if any of them work
        String lTimestamp = null;
        final String[] possibleKeys = { "Mastered date", "Encoded date" };
        for (String lKey : possibleKeys) {
            if (!(lTimestamp = getMovieMetadata(f, lKey)).isEmpty()) {
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
     * Returns the camera model of the movie
     * @param f the file that is being analysed
     * @return the file camera model name
     */
    private String getMovieCameraModel(File f) {
        // Cycle through all possible keys, break if any of them work
        String lCameraModel = null;
        final String[] possibleKeys = { "Writing application", "Title" };
        for (String lKey : possibleKeys) {
            if (!(lCameraModel = getMovieMetadata(f, lKey)).isEmpty()) {
                break;
            }
        }

        // Internal conversion of frequent formats
        if ("CanonMVI06".equals(lCameraModel)) {
            lCameraModel = "canon_ixus70";
        }

        // Get User confirmation
        if (!movieCameraModel.equals(lCameraModel)) {    // Compare the current Camera Model with the previous file's (default is @@No Camera Model@@)
            movieCameraModel = lCameraModel;             // If it is different, ask the user which will be the "User" Camera Model
            int lConfirmResponse;
            do {
                movieCameraModelUser = JOptionPane.showInputDialog("Camera Model for file " + f.getName() + " (and similar):", lCameraModel).trim().toLowerCase().replaceAll("[^\\w]", "_");
                // Confirm
                lConfirmResponse = JOptionPane.showConfirmDialog(null, "Camera Model information for file " + f.getName() +
                        " and similar will be \n\t\t\"" + movieCameraModelUser + "\"\nConfirm?", "Input Confirmation", JOptionPane.YES_NO_CANCEL_OPTION);
                if (JOptionPane.CANCEL_OPTION == lConfirmResponse) {
                    JOptionPane.showMessageDialog(null, "Conversion Cancelled.", "Media Classifier", JOptionPane.INFORMATION_MESSAGE);
                    System.exit(0);
                }
            } while (JOptionPane.YES_OPTION != lConfirmResponse);
        }
        return movieCameraModelUser;

    }

    /**
     * EXIF metadata reading methods.
     */

    /**
     * Populates the exifMetadata property with data for the EXIF analysis.
     * @param f the file that is being analysed
     * @return success
     */
    private boolean readExifMetadata(File f) {
        if (exifMetadata != null) return true;
        try {
            exifMetadata = ImageMetadataReader.readMetadata(f);
        } catch (ImageProcessingException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Returns the EXIF timestamp of the photo
     * @param f the file that is being analysed
     * @return the proposed file timestamp or null in case of error
     */
    private Date getExifTimestamp(File f) {
        if (!readExifMetadata(f)) return null;      // In case of any EXIF error, return null

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
        if (!readExifMetadata(f)) return "";      // In case of any EXIF error, return ""

        // Get Camera MOdel from EXIF
        ExifIFD0Directory directory = exifMetadata.getDirectory(ExifIFD0Directory.class);
        String lCameraModel;
        try {
            lCameraModel = directory.getDescription(ExifIFD0Directory.TAG_MAKE) + " " + directory.getDescription(ExifIFD0Directory.TAG_MODEL);
        } catch (NullPointerException e) {  // This exception occurs when the photo was modified, hence there is EXIF but no EXIF timestamp
            e.printStackTrace();
            return "";      // In case of any EXIF error, return ""
        }

        // Internal conversion of frequent formats
        if ("Canon DIGITAL IXUS 70".equals(lCameraModel)) {
            lCameraModel = "canon_ixus70_101";
        } else if ("NIKON CORPORATION NIKON D3200".equals(lCameraModel)) {
            lCameraModel = "nikon_d3200_100";
        }
        
        // Get User confirmation
        if (!exifCameraModel.equals(lCameraModel)) {    // Compare the current Camera Model with the previous file's (default is @@No Camera Model@@)
            exifCameraModel = lCameraModel;             // If it is different, ask the user which will be the "User" Camera Model
            int lConfirmResponse;
            do {
                exifCameraModelUser = JOptionPane.showInputDialog("Camera Model for file " + f.getName() + " (and similar):", lCameraModel).trim().toLowerCase().replaceAll("[^\\w]", "_");
                // Confirm
                lConfirmResponse = JOptionPane.showConfirmDialog(null, "Camera Model information for file " + f.getName() +
                        " and similar will be \n\t\t\"" + exifCameraModelUser + "\"\nConfirm?", "Input Confirmation", JOptionPane.YES_NO_CANCEL_OPTION);
                if (JOptionPane.CANCEL_OPTION == lConfirmResponse) {
                    JOptionPane.showMessageDialog(null, "Conversion Cancelled.", "Media Classifier", JOptionPane.INFORMATION_MESSAGE);
                    System.exit(0);
                }
            } while (JOptionPane.YES_OPTION != lConfirmResponse);
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
        lTimeStamp = ArrayUtils.contains(fPhotoExtensions, lFileExtension) ? getExifTimestamp(f) : getMovieTimeStamp(f);
        if (null == lTimeStamp) {
            // Get Filestamp from File Last Modified
            lTimeStamp = new Date(f.lastModified());
        }
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(lTimeStamp);
    }

    /**
     * Tries to get the camera model name, and adapts it if needed
     * @param f the file that is being analysed
     * @return the camera model name
     */
    private String getFileCameraModel(File f) {
        return ArrayUtils.contains(fPhotoExtensions, getFileExtension(f)) ? getExifCameraModel(f) : getMovieCameraModel(f);
    }

    /**
     * Checks whether the file can be renamed (if file has not yet been processed)
     * @param f the file that is being analysed
     * @return true if file needs to be processed
     */
    private boolean isFileAlreadyHandled(File f) {
        // Avoid processing already processed files: Accept all names that do not start by "my" timestamp
//        if (f.getName().toLowerCase().endsWith(".thm")) {
//            return true;
//        }
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
        lNewFilePath += "-" + f.getName().toLowerCase().replaceAll("[^\\w.]", "_");

        // Add folder (in this case it is the same)
        lNewFilePath = f.getParent() + File.separator + lNewFilePath;
        return lNewFilePath;
    }

    /**
     * Gets the new Filename path for media, removing the input Regex and prefixing it with Timestamp and Camera Model
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     */
    private String getNewFilePath_RemoveRegexAddPrefixTimestampCamera(File f) {
        String lNewFilePath = getFilePrefix_TimestampCamera(f);
        if (lNewFilePath.isEmpty()) return "";
        String lOldFilename = f.getName().toLowerCase().replaceAll(replaceRegex, "");
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

        // Add folder (in this case it is the same)
        lNewFilePath = f.getParent() + File.separator + lNewFilePath;
        return lNewFilePath;
    }
}