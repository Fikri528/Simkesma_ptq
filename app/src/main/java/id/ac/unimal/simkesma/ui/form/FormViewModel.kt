package id.ac.unimal.simkesma.ui.form

import android.net.Uri
import androidx.lifecycle.ViewModel

class FormViewModel : ViewModel() {
    var fileUri: Uri? = null
    var fileName: String = ""
}
