package com.trustlend.dao;

import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Upload;

import java.util.List;

/** Statement files uploaded by borrowers. */
public interface UploadDao extends GenericDao<Upload> {

    List<Upload> findByVendor(long vendorId) throws DataAccessException;

    List<Upload> findByStatus(Upload.Status status) throws DataAccessException;

    void updateStatus(long uploadId, Upload.Status status) throws DataAccessException;
}
