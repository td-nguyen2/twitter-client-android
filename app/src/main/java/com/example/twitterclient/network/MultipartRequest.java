package com.example.twitterclient.network;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public class MultipartRequest extends Request<JSONObject> {

    private final String boundary = "Boundary-" + UUID.randomUUID();
    private final String content;
    private final byte[] mediaBytes;
    private final String mimeType;
    private final String fileFieldName;
    private final Response.Listener<JSONObject> listener;
    private final Map<String, String> headers;

    public MultipartRequest(
            String url,
            String content,
            byte[] mediaBytes,
            String mimeType,
            Map<String, String> headers,
            Response.Listener<JSONObject> listener,
            Response.ErrorListener errorListener
    ) {
        super(Method.POST, url, errorListener);
        this.content = content;
        this.fileFieldName = "media";
        this.mediaBytes = mediaBytes;
        this.mimeType = mimeType;
        this.headers = headers;
        this.listener = listener;
    }
    public MultipartRequest(
            String url,
            byte[] mediaBytes,
            String mimeType,
            String fileFieldName,
            Map<String, String> headers,
            Response.Listener<JSONObject> listener,
            Response.ErrorListener errorListener
    ) {
        super(Method.POST, url, errorListener);

        this.content = null;
        this.fileFieldName = fileFieldName;
        this.mediaBytes = mediaBytes;
        this.mimeType = mimeType;
        this.headers = headers;
        this.listener = listener;
    }
    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public String getBodyContentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    @Override
    public byte[] getBody() {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            // Determine the file extension
            String extension;

            switch (mimeType) {
                case "image/png":
                    extension = ".png";
                    break;
                case "image/gif":
                    extension = ".gif";
                    break;
                case "image/webp":
                    extension = ".webp";
                    break;
                default:
                    extension = ".jpg";
                    break;
            }
            // PART 1: Tweet text (only for tweet uploads)
            if (content != null) {
                write(output, "--" + boundary + "\r\n");
                write(output, "Content-Disposition: form-data; name=\"content\"\r\n");
                write(output, "\r\n");
                write(output, content + "\r\n");
            }

            // PART 2: Image or GIF
            write(output, "--" + boundary + "\r\n");
            write(output,
                    "Content-Disposition: form-data; name=\""
                            + fileFieldName
                            + "\"; filename=\"upload"
                            + extension
                            + "\"\r\n");
            write(output, "Content-Type: " + mimeType + "\r\n");
            write(output, "\r\n");

            output.write(mediaBytes);

            // End multipart request
            write(output, "\r\n--" + boundary + "--\r\n");

            return output.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare media upload", e);
        }
    }

    private void write(ByteArrayOutputStream output, String text)
            throws IOException {
        output.write(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected Response<JSONObject> parseNetworkResponse(
            NetworkResponse response
    ) {
        try {
            String json = new String(
                    response.data,
                    StandardCharsets.UTF_8
            );

            return Response.success(
                    new JSONObject(json),
                    HttpHeaderParser.parseCacheHeaders(response)
            );
        } catch (Exception e) {
            return Response.error(new com.android.volley.ParseError(e));
        }
    }

    @Override
    protected void deliverResponse(JSONObject response) {
        listener.onResponse(response);
    }
}
