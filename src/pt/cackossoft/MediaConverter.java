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
    private final String[] fConverters = { "Check Converted Files",
                                           "Canon Ixus 70",
                                           "Nikon D3200",
                                           "Samsung Galaxy S2/S3/Tab",
                                           "Samsung Galaxy Ace",
                                           "HTC Wildfire",
                                           "Simple Rename With Camera"  };

    // private final String[] fMovieExtensions = { ".avi", ".mpg", ".mov" };
    private final String[] fPhotoExtensions = { ".jpg", ".png", ".gif" };
    private Metadata exifMetadata;

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
            case 1: return getNewFilePath_canonixus70(f);
            case 2: return getNewFilePath_nikon3200(f);
            case 3: return getNewFilePath_samsung(f);
            case 4: return getNewFilePath_samsungAce(f);
            case 5: return getNewFilePath_htcWildfire2(f);
            case 6: return getNewFilePath_simpleRename(f);
        }
        return "";
    }

    /**
     * Runs the Media Converter/Verifier
     * @param aDefaultFolder the default folder for the media
     * @return success
     */
    public int main(String aDefaultFolder) {
        for (;;) {
            // Get Path of the folder to be converted
            String lSrcFolderPath = getSrcFolderPath(aDefaultFolder);
            if ("".equals(lSrcFolderPath)) {
                JOptionPane.showMessageDialog(null, "No Folder was selected. Exiting...", "Media Classifier", JOptionPane.INFORMATION_MESSAGE);
                break;
            }
            // Get Converter
            String lConverter = (String) JOptionPane.showInputDialog(null, "Apply file rename converter:", "Media Converter", JOptionPane.QUESTION_MESSAGE, null, fConverters, fConverters[0]);
            // Confirm Action
            if (JOptionPane.OK_OPTION != JOptionPane.showConfirmDialog(null, "Please Confirm the parameters:\n\nSelected folder: " + lSrcFolderPath + "\nSelected Converter: " + lConverter, "Input Confirmation", JOptionPane.OK_CANCEL_OPTION)) {
                JOptionPane.showMessageDialog(null, "Conversion Cancelled.", "Media Classifier", JOptionPane.INFORMATION_MESSAGE);
                continue;
            }

            File[] listOfFiles = new File(lSrcFolderPath).listFiles();
            if (listOfFiles != null) {
                for (File listOfFile : listOfFiles) {
                    if (listOfFile.isFile()) {
                        exifMetadata = null;
                        File f = new File(listOfFile.getAbsolutePath());
                        String lNewName = getNewFilePath(f, lConverter);
                        if (!"".equals(lNewName)) {
                            System.out.println("Rename file: " + f.getAbsolutePath() + " to " + lNewName + (renameFile(f, lNewName) ? ": Renamed successfully" : ": Error in rename"));
                        }
                    }
                }
            }
            JOptionPane.showMessageDialog(null, "Conversion finished.", "Media Classifier", JOptionPane.INFORMATION_MESSAGE);
        }
        return 0;
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
            return input == null ? "" : input.split(": ")[1].trim();
        }
        return "";
    }

    /**
     * Returns the timestamp of the movie
     * @param f the file that is being analysed
     * @return the proposed file timestamp or null in case of error
     */
    private Date getMovieTimeStamp(File f) {
        String lMetadata = getMovieMetadata(f, "Mastered date");
        try {
            return new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy").parse(lMetadata);
        } catch (ParseException e) {
            // do nothing;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(lMetadata);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Returns the camera model of the movie
     * @param f the file that is being analysed
     * @return the file camera model name
     */
    private String getMovieCameraModel(File f) {
        String lCameraModel = getMovieMetadata(f, "Writing application");
        return lCameraModel.equals("") ? getMovieMetadata(f, "Title") : "";
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
            return null;      // In case of any EXIF error, return ""
        }
    }

    /**
     * Returns the EXIF camera model of the photo
     * @param f the file that is being analysed
     * @return the file camera model name
     */
    private String getExifCameraModel(File f) {
        if (!readExifMetadata(f)) return "";      // In case of any EXIF error, return ""

        // Get Filestamp from EXIF
        ExifIFD0Directory directory = exifMetadata.getDirectory(ExifIFD0Directory.class);
        try {
            return directory.getDescription(ExifIFD0Directory.TAG_MAKE) + " " + directory.getDescription(ExifIFD0Directory.TAG_MODEL);
        } catch (NullPointerException e) {  // This exception occurs when the photo was modified, hence there is EXIF but no EXIF timestamp
            e.printStackTrace();
            return "";      // In case of any EXIF error, return ""
        }
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
        if (lTimeStamp == null) {
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
        String lFileExtension = getFileExtension(f);
        String lCameraModel = ArrayUtils.contains(fPhotoExtensions, lFileExtension) ? getExifCameraModel(f) : getMovieCameraModel(f);
        lCameraModel = lCameraModel.trim().toLowerCase().replaceAll("[^\\w]", "_");
        if (lCameraModel.equals("canon_digital_ixus_70")) {
            lCameraModel = "canon_ixus70";
        }
        return lCameraModel;
    }

    /**
     * Checks whether the file can be renamed (if file has not yet been processed)
     * @param f the file that is being analysed
     * @return true if file needs to be processed
     */
    private boolean isInvalidFileForRenaming(File f) {
        // Avoid processing already processed files: Accept all names that do not start by "my" timestamp
        return f.getName().matches("^(19|20)\\d\\d(0[1-9]|1[012])(0[1-9]|[12][0-9]|3[01])_([01][0-9]|2[0-3])[0-5][0-9][0-5][0-9]-.*");
    }

    /**
     * Gets a new prefix for the file, which starts with a file timestamp.
     * @param f the file that is being analysed
     * @return "" if a problem occurred (e.g., the file already has the new format); the new prefix name otherwise.
     */
    private String getFilePrefix_Timestamp(File f) {
        return isInvalidFileForRenaming(f) ? "" : getFileTimestamp(f);
    }

    /**
     * Gets a new prefix for the file, which starts with a file timestamp, followed by the camera model.
     * @param f the file that is being analysed
     * @return "" if a problem occurred (e.g., the file already has the new format); the new prefix name otherwise.
     */
    private String getFilePrefix_TimestampCamera(File f) {
        String lFilePrefix = getFilePrefix_Timestamp(f);
        if ("".equals(lFilePrefix)) return "";
        String lCameraModel = getFileCameraModel(f);
        return lCameraModel.equals("") ? "" : lFilePrefix + "-" + lCameraModel;
    }

    /**
     * Gets the new Filename path for media taken on: All Samsung Phones except Ace (change the lStaticInfo)
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     */
    private String getNewFilePath_samsung(File f) {
        if (isInvalidFileForRenaming(f)) return "";

        String lStaticInfo = getFileCameraModel(f);
        String lTimestamp = f.getName().substring(0, 15);   // It is better to use the timestamp on the filename
        String lNewFilePath = lTimestamp + lStaticInfo + getFileExtension(f);

        // Add folder (in this case it is the same)
        lNewFilePath = f.getParent() + File.separator + lNewFilePath;
        return lNewFilePath;
    }

    /**
     * Gets the new Filename path for media taken on: Samsung Galaxy Ace
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     */
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
     * Gets the new Filename path for media taken on: Canon IXUS 70 (book 101)
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     */
    private String getNewFilePath_canonixus70(File f) {
        // Avoid processing already processed files: Format of the filenames: "IMG_" or "MVI_" + dddd .jpg or .avi
        if (!f.getName().substring(0, 4).equals("IMG_") && !f.getName().substring(0, 4).equals("MVI_"))  { return ""; }
        String lStaticInfo = "-canon_ixus70-101";
        String lNewFilePath = getFileTimestamp(f) + lStaticInfo + "-" + f.getName().toLowerCase();

        // Add folder (in this case it is the same)
        lNewFilePath = f.getParent() + File.separator + lNewFilePath;
        return lNewFilePath;
    }

    /**
     * Gets the new Filename path for media taken on: Nikon D3200 (book 100)
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     */
    private String getNewFilePath_nikon3200(File f) {
        // Avoid processing already processed files: Format of the filenames: "DSC_" + dddd .jpg or .mov
        if (!f.getName().substring(0, 4).equals("DSC_"))  { return ""; }
        String lStaticInfo = "-nikon_d3200-100";
        String lNewFilePath;
        String lNumber = f.getName().substring(4, 8);       // Because we need to increase the photo nr by 261
//        int lNewNumber = Integer.parseInt(lNumber);       // This is used if there is any offset needed in the photo number.
//        lNumber = String.format("%0,4d", lNewNumber + 261);
        lNewFilePath = getFileTimestamp(f) + lStaticInfo + "-" + "dsc_" + lNumber + f.getName().substring(8).toLowerCase();//f.getName().toLowerCase();

        // Add folder (in this case it is the same)
        lNewFilePath = f.getParent() + File.separator + lNewFilePath;
        return lNewFilePath;
    }

    /**
     * Gets the new Filename path for media taken on: HTC Wildfire (before 2012)
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     */
    private String getNewFilePath_htcWildfire(File f) {
        String lStaticInfo = f.getName().substring(0, 5);
        // Avoid processing already processed files: Format of the filenames: "IMAG0" or "VIDEO" + dddd .jpg or .mov
        if (!(lStaticInfo.equals("IMAG0") || lStaticInfo.equals("VIDEO")))  { return ""; }
        lStaticInfo = "-htc";
        String lNewFilePath;
        lNewFilePath = getFileTimestamp(f) + lStaticInfo + "-" + f.getName().toLowerCase();//f.getName().toLowerCase();

        // Add folder (in this case it is the same)
        lNewFilePath = f.getParent() + File.separator + lNewFilePath;
        return lNewFilePath;
    }

    /**
     * Gets the new Filename path for media taken on: HTC Wildfire (after 2012)
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     */
    private String getNewFilePath_htcWildfire2(File f) {
        String lStaticInfo = f.getName().substring(0, 4);
        // Avoid processing already processed files: Format of the filenames: "IMG_" or "VID_" + dddd .jpg or .mov
        if (!(lStaticInfo.equals("IMG_") || lStaticInfo.equals("VID_")))  { return ""; }
        lStaticInfo = "-htc";
        String lNewFilePath = f.getName().substring(4, 19) + lStaticInfo + f.getName().substring(19).toLowerCase();

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
     * Gets the new Filename path for media taken on any folder
     * @param f the file that is being analysed
     * @return "" if the file does not need renaming; the new name otherwise.
     */
    private String getNewFilePath_simpleRename(File f) {
        String lNewFilePath = getFilePrefix_TimestampCamera(f);
        if (lNewFilePath.equals("")) return "";
        lNewFilePath += "-" + f.getName().toLowerCase().replaceAll("[^\\w.]", "_");

        // Add folder (in this case it is the same)
        lNewFilePath = f.getParent() + File.separator + lNewFilePath;
        return lNewFilePath;
    }
}