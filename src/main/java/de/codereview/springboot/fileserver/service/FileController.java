package de.codereview.springboot.fileserver.service;

import de.codereview.springboot.fileserver.service.converter.ConverterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/fs")
public class FileController
{
    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final FileService fileService;
    private final MimeTypeService mimeTypeService;
    private final ConverterService converterService;

    private static final int PREFIX_PATH_LENGTH = "/fs/".length();

    @Autowired
    public FileController(FileService fileService, MimeTypeService mimeTypeService, ConverterService converterService)
    {
        this.fileService = fileService;
        this.mimeTypeService = mimeTypeService;
        this.converterService = converterService;
    }

    @RequestMapping(value = "/{box}/**", method = RequestMethod.GET)
    public Object file(@PathVariable String box,
                       HttpServletRequest request,
                       @RequestHeader Map<String, String> header,
                       HttpServletResponse response)
    {
        String path = (String) request.getAttribute(
            HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        // alternative: request.getServletPath();

        path = path.substring(PREFIX_PATH_LENGTH + box.length() + 1);
        Path filePath = fileService.getFilePath(box, path);
        try {
            if (Files.isDirectory(filePath)) {
                throw new RuntimeException("accessing directories not implemented in service api");
            } else {
                String mimetype = mimeTypeService.detectMimeType(filePath);

                String acceptHeader = header.get(HttpHeaders.ACCEPT);
                log.debug("accept header is '{}'", acceptHeader);
                byte[] bytes = fileService.readFile(filePath);
                String filename = filePath.getFileName().toString();
                List<MediaType> acceptedTypes = MediaType.parseMediaTypes(acceptHeader);
                for (MediaType mediaType : acceptedTypes) {
                    String target = mediaType.toString();
                    if (converterService.isConversionAvailable(mimetype, target)) {
                        log.debug("conversion from {} to {}", mimetype, target);
                        // TODO better title than filename?
                        bytes = converterService.convert(bytes, mimetype, target, filename);
                        mimetype = target;
                        filename = filename + converterService.getTargetExtension(target);
                        // TODO: redirect to url with extended extension?
//                        response.sendRedirect("some-url"); // maybe forward, not rediret
                        break;
                    }
                }
                response.setHeader(HttpHeaders.CONTENT_TYPE, mimetype);
                response.setHeader(HttpHeaders.CONTENT_LENGTH, "" + bytes.length);
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "filename=" + filename);

                Map<String, String> metadata = fileService.getFileMetadata(filePath);
                metadata.forEach(response::setHeader);
                return bytes;
            }
        } catch (IOException e) {
            String msg = "Error accesing box storage";
            log.error(msg);
            throw new IllegalArgumentException(msg, e);
        }
    }
}