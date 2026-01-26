package dopaminelite.payment_portal.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic DTO representing the standard response format from the BFF service.
 * @param <T> The type of items in the response data
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BffStandardResponse<T> {

    /**
     * Indicates whether the request was successful.
     */
    private boolean success;

    /**
     * The response data containing items and total count.
     */
    private ResponseData<T> data;

    /**
     * Inner class representing the data structure.
     * @param <T> The type of items in the list
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseData<T> {

        /**
         * List of items returned by the API.
         */
        private List<T> items;

        /**
         * Total count of items.
         */
        private int total;
    }
}
