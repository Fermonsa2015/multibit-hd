package org.multibit.hd.ui.fest.requirements;

import com.google.common.collect.Maps;
import org.fest.swing.fixture.FrameFixture;
import org.multibit.hd.ui.fest.use_cases.contacts.*;

import java.util.Map;

/**
 * <p>FEST Swing UI test to provide:</p>
 * <ul>
 * <li>Exercise the "contacts" screen to verify its wizards show correctly</li>
 * </ul>
 *
 * @since 0.0.1
 *  
 */
public class ContactsScreen {

  public static void verifyUsing(FrameFixture window) {

    Map<String,Object> parameters = Maps.newHashMap();

    // Select the contacts screen
    new ShowContactsScreenUseCase(window).execute(parameters);

    // Click Add then immediate Cancel
    new AddThenCancelContactUseCase(window).execute(parameters);

    // Click Add and fill in "Alice"
    new AddAliceContactUseCase(window).execute(parameters);

    // Click Add and fill in "Bob"
    new AddBobContactUseCase(window).execute(parameters);

    // Click Edit and fill in Bob's extra info
    new EditBobContactUseCase(window).execute(parameters);

    // Select Alice and Bob then use multi-edit
    new EditAliceAndBobContactUseCase(window).execute(parameters);

    // Click Add and fill in "Uriah"
    new AddUriahContactUseCase(window).execute(parameters);

    // Select Uriah and Click Delete
    new DeleteUriahContactUseCase(window).execute(parameters);
  }
}