package dopaminelite.payment_portal.dto.calendar;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A class (`videoms` folder) reference — opaque {@code id} plus a display-name snapshot,
 * both resolved and supplied by the BFF. This backend never learns anything else about it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassRefDto {

    private String id;

    private String name;

}
