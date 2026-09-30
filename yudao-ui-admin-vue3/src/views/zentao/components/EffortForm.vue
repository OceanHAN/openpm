<template>
  <el-dialog v-model="visible" :title="form.id ? '修改工时' : '登记工时'" width="640px" append-to-body @closed="onClosed">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="任务剩余工时以「最后一条工时」为准"
      description="已消耗 = 所有工时之和；剩余 = 最后一条工时里填的值（不是预计减已消耗）。所以每条工时都要顺手声明一下「现在还剩多少」。剩余填 0，任务会自动变成已完成。"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="任务" prop="taskId">
        <el-input-number
          v-model="form.taskId"
          :min="1"
          :controls="false"
          :disabled="!!fixedTaskId"
          placeholder="任务编号"
          class="!w-200px"
        />
        <span v-if="taskLabel" class="ml-10px text-gray-500">#{{ form.taskId }} {{ taskLabel }}</span>
      </el-form-item>
      <el-form-item label="谁报的" prop="account">
        <el-select v-model="form.account" filterable allow-create default-first-option clearable
                   placeholder="留空 = 当前登录用户" class="!w-240px">
          <el-option v-for="u in userList" :key="u.account" :label="`${u.realname || u.account}（${u.account}）`"
                     :value="u.account!" />
        </el-select>
      </el-form-item>
      <el-form-item label="工作日期" prop="date">
        <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD"
                        placeholder="选择日期" class="!w-240px" />
      </el-form-item>
      <el-form-item label="消耗工时" prop="consumed">
        <el-input-number v-model="form.consumed" :min="0" :precision="2" :step="0.5" class="!w-200px" />
        <span class="ml-10px text-gray-500">小时</span>
      </el-form-item>
      <el-form-item label="剩余工时" prop="left">
        <el-input-number v-model="form.left" :min="0" :precision="2" :step="0.5" class="!w-200px" />
        <span class="ml-10px text-gray-500">留空则按「当前剩余 - 本次消耗」推算</span>
      </el-form-item>
      <el-form-item label="起止时间">
        <el-input v-model="form.begin" placeholder="0900" maxlength="4" class="!w-100px" />
        <span class="mx-8px">~</span>
        <el-input v-model="form.end" placeholder="1800" maxlength="4" class="!w-100px" />
        <span class="ml-10px text-gray-500">四位 HHMM，可留空</span>
      </el-form-item>
      <el-form-item label="工作内容" prop="work">
        <el-input v-model="form.work" type="textarea" :rows="3" maxlength="500" show-word-limit
                  placeholder="这段时间具体做了什么" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as EffortApi from '@/api/zentao/effort'
import * as OrgApi from '@/api/zentao/organization'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'ZentaoEffortForm' })

const emit = defineEmits(['success'])

/** 今天的 yyyy-MM-dd，作为新建工时时的默认日期 */
const todayStr = () => formatDate(new Date(), 'YYYY-MM-DD')
const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const userList = ref<OrgApi.OrgUserVO[]>([])
/** 从任务页打开时任务号是固定的，不让改 */
const fixedTaskId = ref<number | undefined>(undefined)
const taskLabel = ref('')

const form = reactive<EffortApi.EffortVO & { taskId?: number }>({
  id: undefined,
  taskId: undefined,
  account: '',
  date: todayStr(),
  consumed: 0,
  left: undefined,
  work: '',
  begin: '',
  end: ''
})

const rules = {
  taskId: [{ required: true, message: '请填写任务编号', trigger: 'blur' }],
  date: [{ required: true, message: '请选择工作日期', trigger: 'change' }],
  consumed: [{ required: true, message: '请填写消耗工时', trigger: 'blur' }],
  work: [{ required: true, message: '请填写工作内容', trigger: 'blur' }]
}

const loadUsers = async () => {
  if (userList.value.length > 0) return
  try {
    userList.value = await OrgApi.getUserList({})
  } catch {
    userList.value = []
  }
}

/**
 * 打开表单
 * @param task 任务（任务页会带上名称，工时页只给编号）
 * @param row  修改时传入现有工时
 */
const open = async (task: { id?: number; name?: string }, row?: EffortApi.EffortVO) => {
  await loadUsers()
  fixedTaskId.value = task.id
  taskLabel.value = task.name || ''
  if (row) {
    Object.assign(form, {
      id: row.id,
      taskId: row.objectID ?? task.id,
      account: row.account,
      date: row.date,
      consumed: row.consumed,
      left: row.left,
      work: row.work,
      begin: row.begin,
      end: row.end
    })
  } else {
    Object.assign(form, {
      id: undefined,
      taskId: task.id,
      account: '',
      date: todayStr(),
      consumed: 0,
      left: undefined,
      work: '',
      begin: '',
      end: ''
    })
  }
  visible.value = true
}

const onClosed = () => {
  fixedTaskId.value = undefined
  taskLabel.value = ''
  formRef.value?.resetFields()
}

const handleSubmit = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form, taskId: form.taskId! } as EffortApi.EffortVO & { taskId: number }
    if (form.id) {
      await EffortApi.updateEffort(payload)
      message.success('工时已修改')
    } else {
      await EffortApi.createEffort(payload)
      message.success('工时已登记')
    }
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>
