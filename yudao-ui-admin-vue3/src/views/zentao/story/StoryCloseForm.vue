<template>
  <Dialog v-model="dialogVisible" title="关闭需求" width="560">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
      <el-form-item label="关闭原因" prop="closedReason">
        <el-select v-model="formData.closedReason" placeholder="请选择关闭原因" class="w-full">
          <el-option
            v-for="o in STORY_CLOSED_REASON_OPTIONS"
            :key="o.value"
            :label="o.label"
            :value="o.value"
          />
        </el-select>
      </el-form-item>

      <!-- 选「重复」时必须指定目标需求，与禅道规则一致 -->
      <el-form-item
        v-if="formData.closedReason === 'duplicate'"
        label="重复需求"
        prop="duplicateStory"
      >
        <el-input-number
          v-model="formData.duplicateStory"
          :min="1"
          :controls="false"
          placeholder="请输入重复的目标需求编号"
          class="w-full"
        />
        <div class="text-gray-400 text-12px mt-5px">
          禅道规则：关闭原因为「重复」时，必须指定重复的目标需求，且该需求必须真实存在。
        </div>
      </el-form-item>

      <el-form-item label="备注" prop="comment">
        <el-input v-model="formData.comment" type="textarea" :rows="3" placeholder="关闭说明（可选）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as StoryApi from '@/api/zentao/story'
import { STORY_CLOSED_REASON_OPTIONS } from './constants'

defineOptions({ name: 'ZentaoStoryCloseForm' })

const message = useMessage()
const dialogVisible = ref(false)
const formRef = ref()

const formData = ref<StoryApi.StoryCloseVO>({
  id: 0,
  closedReason: 'done',
  duplicateStory: undefined,
  comment: ''
})

const formRules = reactive({
  closedReason: [{ required: true, message: '关闭原因不能为空', trigger: 'change' }],
  duplicateStory: [
    {
      validator: (_r: any, _v: any, cb: any) => {
        if (formData.value.closedReason === 'duplicate' && !formData.value.duplicateStory) {
          cb(new Error('关闭原因为「重复」时必须填写目标需求编号'))
        } else {
          cb()
        }
      },
      trigger: 'blur'
    }
  ]
})

const open = (id: number) => {
  dialogVisible.value = true
  formData.value = { id, closedReason: 'done', duplicateStory: undefined, comment: '' }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  await StoryApi.closeStory(formData.value)
  message.success('需求已关闭')
  dialogVisible.value = false
  emit('success')
}
</script>
