package kr.co.mrk13.android.androidfloatingmusic.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceFragmentCompat
import kr.co.mrk13.android.androidfloatingmusic.R
import kr.co.mrk13.android.androidfloatingmusic.databinding.ActivitySettingBinding


/**
 * @author ross.
 */
class SettingActivity : AppCompatActivity() {

    private var mBinding: ActivitySettingBinding? = null
    private val binding get() = mBinding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fragment = SettingFragment()
        supportFragmentManager
            .beginTransaction()
            .replace(binding.settingLayout.id, fragment)
            .commit()
    }

    override fun onDestroy() {
        mBinding = null
        super.onDestroy()
    }
}

class SettingFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}